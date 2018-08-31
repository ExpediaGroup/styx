/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.client.connectionpool;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingWindowReservoir;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.api.extension.Origin;
import org.slf4j.Logger;
import rx.Observable;
import rx.Subscriber;

import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static com.hotels.styx.client.connectionpool.ConnectionPoolStatsCounter.NULL_CONNECTION_POOL_STATS;
import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A connection pool implementation.
 */
public class SimpleConnectionPool implements ConnectionPool, Comparable<ConnectionPool>, Connection.Listener {
    private static final Logger LOG = getLogger(SimpleConnectionPool.class);

    private final Origin origin;

    private final ConnectionPoolSettings connectionPoolSettings;
    private final Connection.Factory connectionFactory;
    private final Queue<Subscriber<? super Connection>> waitingSubscribers;
    private final Queue<Connection> availableConnections;
    private final Set<Connection> borrowedConnections;
    private final ConnectionPoolStatsCounter stats;

    private final AtomicInteger busyConnections = new AtomicInteger(0);
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicInteger connectionFailures = new AtomicInteger(0);
    private final AtomicInteger closedConnections = new AtomicInteger(0);
    private final AtomicInteger terminatedConnections = new AtomicInteger(0);

    /**
     * Constructs an instance that will record stats.
     *
     * @param origin                 origin to connect to
     * @param connectionPoolSettings connection pool configuration
     * @param connectionFactory      connection factory
     */
    public SimpleConnectionPool(Origin origin, ConnectionPoolSettings connectionPoolSettings, Connection.Factory connectionFactory) {
        this(origin, connectionPoolSettings, connectionFactory, true);
    }

    /**
     * Constructs an instance that can be configured not to record stats.
     *
     * @param origin                 origin to connect to
     * @param connectionPoolSettings connection pool configuration
     * @param connectionFactory      connection factory
     * @param recordStats            true if stats should be recorded
     */
    public SimpleConnectionPool(Origin origin, ConnectionPoolSettings connectionPoolSettings, Connection.Factory connectionFactory, boolean recordStats) {
        this.connectionPoolSettings = requireNonNull(connectionPoolSettings);
        this.origin = requireNonNull(origin);
        this.connectionFactory = requireNonNull(connectionFactory);
        this.availableConnections = new ConcurrentLinkedDeque<>();
        this.borrowedConnections = newSetFromMap(new ConcurrentHashMap<>());
        this.waitingSubscribers = new ConcurrentLinkedDeque<>();
        this.stats = recordStats ? new ConnectionPoolStats() : NULL_CONNECTION_POOL_STATS;
    }

    private static <T> void removeEachAndProcess(Queue<T> queue, Consumer<T> consumer) {
        while (true) {
            T item = queue.poll();

            if (item == null) {
                break;
            }

            consumer.accept(item);
        }
    }

    @Override
    public int compareTo(ConnectionPool other) {
        return this.origin.compareTo(other.getOrigin());
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Observable<Connection> borrowConnection() {
        if (busyConnections.incrementAndGet() <= connectionPoolSettings.maxConnectionsPerHost()) {
            return doBorrowConnection();
        }

        busyConnections.decrementAndGet();

        TimeoutObservableFactory timeoutObservableFactory = new TimeoutObservableFactory(origin, waitingSubscribers);
        return timeoutObservableFactory.create(stats, connectionPoolSettings);
    }

    @Override
    public boolean returnConnection(Connection connection) {
        stats.recordTimeToFirstByte(connection.getTimeToFirstByteMillis());
        if (connection.isConnected()) {
            Subscriber<? super Connection> subscriber = waitingSubscribers.poll();
            if (subscriber != null) {
                subscriber.onNext(connection);
                subscriber.onCompleted();
            } else {
                // todo this synchronization can probably be removed
                synchronized (connection) {
                    busyConnections.decrementAndGet();
                    borrowedConnections.remove(connection);
                    availableConnections.add(connection);
                }
            }
        } else {
            connectionClosedInternal(connection);
        }

        return false;
    }

    @Override
    public boolean closeConnection(Connection connection) {
        boolean removed = borrowedConnections.remove(connection);
        if (removed) {
            busyConnections.decrementAndGet();
            closedConnections.incrementAndGet();
            connection.close();
        }
        return true;
    }

    private void connectionClosedInternal(Connection connection) {
        boolean removed = borrowedConnections.remove(connection);
        if (removed) {
            busyConnections.decrementAndGet();
            connection.close();
        }

        removed = availableConnections.remove(connection);
        if (removed) {
            connection.close();
        }
    }

    @Override
    public void connectionClosed(Connection connection) {
        if (connection != null) {
            terminatedConnections.incrementAndGet();
            connectionClosedInternal(connection);
        }
    }

    private Observable<Connection> doBorrowConnection() {
        return getNextActiveConnection()
                .map(connection -> {
                    borrowedConnections.add(connection);
                    return Observable.just(connection);
                }).orElseGet(this::createConnection);
    }

    private Observable<Connection> createConnection() {
        connectionAttempts.incrementAndGet();
        return connectionFactory.createConnection(origin, new ConnectionSettings(connectionPoolSettings.connectTimeoutMillis()))
                .doOnError(throwable -> {
                    busyConnections.decrementAndGet();
                    connectionFailures.incrementAndGet();
                })
                .map(connection -> {
                    connection.addConnectionListener(SimpleConnectionPool.this);
                    borrowedConnections.add(connection);
                    return connection;
                });
    }

    private Optional<Connection> getNextActiveConnection() {
        return Optional.ofNullable(getNextActiveConnectionCloseOnDeadConnection());
    }

    private Connection getNextActiveConnectionCloseOnDeadConnection() {
        Connection connection = availableConnections.poll();
        while (connection != null && !connection.isConnected()) {
            connection = this.availableConnections.poll();
        }
        return connection;
    }

    @Override
    public boolean isExhausted() {
        return pendingConnectionCount() >= connectionPoolSettings.maxPendingConnectionsPerHost();
    }

    private int pendingConnectionCount() {
        return waitingSubscribers.size();
    }

    @Override
    public Stats stats() {
        return this.stats;
    }

    @Override
    public void close() {
        removeEachAndProcess(waitingSubscribers, subscriber ->
                subscriber.onError(new RuntimeException("Connection pool closed")));
        removeEachAndProcess(availableConnections, Connection::close);
        borrowedConnections.forEach(Connection::close);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(origin);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SimpleConnectionPool other = (SimpleConnectionPool) obj;
        return Objects.equal(this.origin, other.origin);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("origin", origin)
                .add("availableConnections", availableConnections)
                .add("stats", stats)
                .toString();
    }

    public ConnectionPoolSettings settings() {
        return connectionPoolSettings;
    }

    private static class TimeoutObservableFactory {
        private final Origin origin;
        private final Queue<Subscriber<? super Connection>> waitingSubscribers;
        private volatile Subscriber<? super Connection> waitingSubscriber;

        TimeoutObservableFactory(Origin origin, Queue<Subscriber<? super Connection>> waitingSubscribers) {
            this.origin = origin;
            this.waitingSubscribers = waitingSubscribers;
        }

        public Observable<Connection> create(ConnectionPool.Stats stats, ConnectionPoolSettings connectionPoolSettings) {
            Observable.OnSubscribe<Connection> onSubscribe = subscriber -> {
                waitingSubscriber = subscriber;
                if (stats.pendingConnectionCount() >= connectionPoolSettings.maxPendingConnectionsPerHost()) {
                    subscriber.onError(new MaxPendingConnectionsExceededException(
                            origin,
                            stats.pendingConnectionCount(),
                            connectionPoolSettings.maxPendingConnectionsPerHost()));
                } else {
                    waitingSubscribers.add(subscriber);
                }
            };

            return Observable.create(onSubscribe)
                    .timeout(connectionPoolSettings.pendingConnectionTimeoutMillis(), MILLISECONDS)
                    .onErrorResumeNext(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            waitingSubscribers.remove(waitingSubscriber);
                            return Observable.error(new MaxPendingConnectionTimeoutException(origin, connectionPoolSettings.pendingConnectionTimeoutMillis()));
                        }
                        return Observable.error(throwable);
                    });
        }
    }

    @VisibleForTesting
    private class ConnectionPoolStats implements ConnectionPoolStatsCounter {
        final Histogram timeToFirstByteHistogram = new Histogram(new SlidingWindowReservoir(50));

        private final Supplier<Long> ttfbSupplier = () -> (long) timeToFirstByteHistogram.getSnapshot().getMean();
        private final Supplier<Long> memoizedTtfbSupplier = memoizeWithExpiration(ttfbSupplier::get, 5, MILLISECONDS)::get;

        @Override
        public int busyConnectionCount() {
            return busyConnections.get();
        }

        @Override
        public int availableConnectionCount() {
            return availableConnections.size();
        }

        @Override
        public int pendingConnectionCount() {
            return waitingSubscribers.size();
        }

        @Override
        public long timeToFirstByteMs() {
            return memoizedTtfbSupplier.get();
        }

        @Override
        public int connectionAttempts() {
            return connectionAttempts.get();
        }

        @Override
        public int connectionFailures() {
            return connectionFailures.get();
        }

        @Override
        public int closedConnections() {
            return closedConnections.get();
        }

        @Override
        public int terminatedConnections() {
            return terminatedConnections.get();
        }

        @Override
        public void recordTimeToFirstByte(long msValue) {
            if (msValue < 0) {
                LOG.warn("illegal time to first byte registered {}", msValue);
            } else {
                timeToFirstByteHistogram.update(msValue);
            }
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("pendingConnections", pendingConnectionCount())
                    .add("busyConnections", busyConnections)
                    .add("maxConnections", connectionPoolSettings.maxConnectionsPerHost())
                    .toString();
        }
    }

    /**
     * Factory to construct instances.
     */
    public static class Factory implements ConnectionPool.Factory {
        private ConnectionPoolSettings connectionPoolSettings;
        private Connection.Factory connectionFactory;
        private boolean recordStats = true;

        public Factory connectionPoolSettings(ConnectionPoolSettings connectionPoolSettings) {
            this.connectionPoolSettings = connectionPoolSettings;
            return this;
        }

        public Factory connectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Factory recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        public SimpleConnectionPool create(Origin origin) {
            return new SimpleConnectionPool(origin, connectionPoolSettings, connectionFactory, recordStats);
        }
    }
}

