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
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import rx.Observable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.RxReactiveStreams.toObservable;

/**
 * A connection pool implementation.
 */
public class ImprovedConnectionPool implements ConnectionPool, Connection.Listener {
    private static final Logger LOG = getLogger(ImprovedConnectionPool.class);

    private ConnectionPoolSettings poolSettings;
    private final ConnectionSettings connectionSettings;
    private final Connection.Factory connectionFactory;
    private final Origin origin;

    private final ConcurrentLinkedDeque<MonoSink<Connection>> waitingSubscribers;
    private final Queue<Connection> activeConnections;
    private final AtomicInteger borrowedCount = new AtomicInteger();
    private final ConnectionPoolStats stats = new ConnectionPoolStats();
    private final AtomicInteger connectionAttempts = new AtomicInteger();
    private final AtomicInteger closedConnections = new AtomicInteger();
    private final AtomicInteger terminatedConnections = new AtomicInteger();
    private final AtomicInteger connectionFailures = new AtomicInteger();


    public ImprovedConnectionPool(Origin origin, ConnectionPoolSettings poolSettings, ConnectionSettings connectionSettings, Connection.Factory connectionFactory) {
        this.origin = origin;
        this.poolSettings = poolSettings;
        this.connectionSettings = connectionSettings;
        this.connectionFactory = connectionFactory;
        this.activeConnections = new ConcurrentLinkedDeque<>();
        this.waitingSubscribers = new ConcurrentLinkedDeque<>();
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Observable<Connection> borrowConnection() {
        return toObservable(borrowConnection2());
    }

    public Publisher<Connection> borrowConnection2() {
        return Mono.create(sink -> {
            Connection connection = dequeue();
            if (connection != null) {
                if (borrowedCount.getAndIncrement() < poolSettings.maxConnectionsPerHost()) {
                    sink.success(connection);
                } else {
                    borrowedCount.decrementAndGet();
                    queueNewConnection(connection);
                }
            } else {
                if (waitingSubscribers.size() >= poolSettings.maxPendingConnectionsPerHost()) {
                    sink.error(new MaxPendingConnectionsExceededException(
                            origin,
                            poolSettings.maxPendingConnectionsPerHost(),
                            poolSettings.maxPendingConnectionsPerHost()));
                } else {
                    this.waitingSubscribers.add(sink.onCancel(() -> waitingSubscribers.remove(sink)));
                    if (borrowedCount.get() < poolSettings.maxConnectionsPerHost()) {
                        newConnection();
                    }
                }
            }
        });
    }

    private Observable<Connection> newConnection(int attempts) {
        if (attempts > 0) {
            return this.connectionFactory.createConnection(this.origin, this.connectionSettings)
                    .onErrorResumeNext(cause -> newConnection(attempts - 1));
        } else {
            return Observable.error(new RuntimeException("Unable to create connection"));
        }
    }

    private void newConnection() {
        connectionAttempts.incrementAndGet();
        newConnection(3)
                .subscribe(
                        this::queueNewConnection,
                        cause -> connectionFailures.incrementAndGet()
                );
    }

    private Connection dequeue() {
        Connection connection = activeConnections.poll();

        while (nonNull(connection) && !connection.isConnected()) {
            connection = activeConnections.poll();
        }

        return connection;
    }

    private void queueNewConnection(Connection connection) {
        MonoSink<Connection> subscriber = waitingSubscribers.poll();
        if (subscriber == null) {
            activeConnections.add(connection);
        } else {
            borrowedCount.incrementAndGet();
            subscriber.success(connection);
        }
    }

    public boolean returnConnection(Connection connection) {
        borrowedCount.decrementAndGet();
        if (connection.isConnected()) {
            queueNewConnection(connection);
        }
        return false;
    }

    public boolean closeConnection(Connection connection) {
        connection.close();
        borrowedCount.decrementAndGet();
        closedConnections.incrementAndGet();

        newConnection();
        return true;
    }

    @Override
    public boolean isExhausted() {
        int usage = borrowedCount.get() + waitingSubscribers.size();
        int limit = poolSettings.maxConnectionsPerHost() + poolSettings.maxPendingConnectionsPerHost();

        return usage >= limit;
    }

    @Override
    public ConnectionPoolSettings settings() {
        return poolSettings;
    }

    @Override
    public void connectionClosed(Connection connection) {
        terminatedConnections.incrementAndGet();
    }

    public ConnectionPool.Stats stats() {
        return this.stats;
    }

    @VisibleForTesting
    private class ConnectionPoolStats implements ConnectionPoolStatsCounter {
        final Histogram timeToFirstByteHistogram = new Histogram(new SlidingWindowReservoir(50));

        private final Supplier<Long> ttfbSupplier = () -> (long) timeToFirstByteHistogram.getSnapshot().getMean();
        private final Supplier<Long> memoizedTtfbSupplier = memoizeWithExpiration(ttfbSupplier::get, 5, MILLISECONDS)::get;

        @Override
        public int availableConnectionCount() {
            return activeConnections.size();
        }

        @Override
        public int busyConnectionCount() {
            return borrowedCount.get();
        }

        @Override
        public int pendingConnectionCount() {
            return waitingSubscribers.size();
        }

        @Override
        public long timeToFirstByteMs() {
            return 0;
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
                    .add("busyConnections", busyConnectionCount())
                    .add("maxConnections", poolSettings.maxConnectionsPerHost())
                    .toString();
        }
    }
}

