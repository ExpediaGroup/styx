/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A connection pool implementation.
 */
public class SimpleConnectionPool implements ConnectionPool, Connection.Listener {
    private static final Logger LOG = getLogger(SimpleConnectionPool.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ConnectionPoolSettings poolSettings;
    private final ConnectionSettings connectionSettings;
    private final Connection.Factory connectionFactory;
    private final Origin origin;

    private final ConcurrentLinkedDeque<MonoSink<Connection>> waitingSubscribers;
    private final Queue<Connection> availableConnections;
    private final AtomicInteger borrowedCount = new AtomicInteger();
    private final SimpleConnectionPool.ConnectionPoolStats stats = new SimpleConnectionPool.ConnectionPoolStats();
    private final AtomicInteger connectionAttempts = new AtomicInteger();
    private final AtomicInteger closedConnections = new AtomicInteger();
    private final AtomicInteger terminatedConnections = new AtomicInteger();
    private final AtomicInteger connectionFailures = new AtomicInteger();
    private final AtomicInteger connectionsInEstablishment = new AtomicInteger();
    private volatile boolean active;


    public SimpleConnectionPool(Origin origin, ConnectionPoolSettings poolSettings, Connection.Factory connectionFactory) {
        this.origin = requireNonNull(origin);
        this.poolSettings = requireNonNull(poolSettings);
        this.connectionSettings = new ConnectionSettings(poolSettings.connectTimeoutMillis());
        this.connectionFactory = requireNonNull(connectionFactory);
        this.availableConnections = new ConcurrentLinkedDeque<>();
        this.waitingSubscribers = new ConcurrentLinkedDeque<>();
        this.active = true;
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Publisher<Connection> borrowConnection() {
        return Mono.<Connection>create(sink -> {
            Connection connection = dequeue();
            if (connection != null) {
                attemptBorrowConnection(sink, connection);
            } else {
                if (waitingSubscribers.size() < poolSettings.maxPendingConnectionsPerHost()) {
                    this.waitingSubscribers.add(sink);
                    sink.onDispose(() -> waitingSubscribers.remove(sink));
                    newConnection();
                } else {
                    sink.error(new MaxPendingConnectionsExceededException(
                            origin,
                            poolSettings.maxPendingConnectionsPerHost(),
                            poolSettings.maxPendingConnectionsPerHost()));
                }
            }
        }).timeout(
                Duration.ofMillis(poolSettings.pendingConnectionTimeoutMillis()),
                Mono.error(() -> new MaxPendingConnectionTimeoutException(origin, connectionSettings.connectTimeoutMillis())));
    }

    private void newConnection() {
        int borrowed = borrowedCount.get();
        int inEstablishment = connectionsInEstablishment.getAndIncrement();

        if ((borrowed + inEstablishment) >= poolSettings.maxConnectionsPerHost()) {
            connectionsInEstablishment.decrementAndGet();
            return;
        }

        connectionAttempts.incrementAndGet();
        newConnection(MAX_ATTEMPTS)
                .doOnNext(it -> it.addConnectionListener(SimpleConnectionPool.this))
                .subscribe(
                        connection -> {
                            connectionsInEstablishment.decrementAndGet();
                            this.queueNewConnection(connection);
                        },
                        cause -> {
                            connectionsInEstablishment.decrementAndGet();
                            connectionFailures.incrementAndGet();
                        }
                );
    }

    private Mono<Connection> newConnection(int attempts) {
        if (attempts > 0) {
            return this.connectionFactory.createConnection(this.origin, this.connectionSettings)
                    .onErrorResume(cause -> newConnection(attempts - 1));
        } else {
            return Mono.error(new RuntimeException("Unable to create connection"));
        }
    }

    @VisibleForTesting
    Connection dequeue() {
        Connection connection = availableConnections.poll();

        while (nonNull(connection) && !connection.isConnected()) {
            connection = availableConnections.poll();
        }

        return connection;
    }

    private void queueNewConnection(Connection connection) {
        MonoSink<Connection> subscriber = waitingSubscribers.poll();
        if (subscriber == null) {
            availableConnections.add(connection);
        } else {
            attemptBorrowConnection(subscriber, connection);
        }
    }

    private void attemptBorrowConnection(MonoSink<Connection> sink, Connection connection) {
        borrowedCount.incrementAndGet();
        sink.onCancel(() -> {
            returnConnection(connection);
        });
        sink.success(connection);
    }

    public boolean returnConnection(Connection connection) {
        if (!active) {
            connection.close();
        } else {
            borrowedCount.decrementAndGet();
            if (connection.isConnected()) {
                queueNewConnection(connection);
            }
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
        availableConnections.remove(connection);
    }

    @Override
    public void close() {
        active = false;
        Connection con;
        while ((con = availableConnections.poll()) != null) {
            con.close();
        }
    }

    public ConnectionPool.Stats stats() {
        return this.stats;
    }

    @VisibleForTesting
    private class ConnectionPoolStats implements Stats {
        @Override
        public int availableConnectionCount() {
            return availableConnections.size();
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
        public int connectionsInEstablishment() {
            return connectionsInEstablishment.get();
        }


        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("\navailableConnections", availableConnectionCount())
                    .add("\npendingConnections", pendingConnectionCount())
                    .add("\nbusyConnections", busyConnectionCount())
                    .add("\nconnectionAttempts", connectionAttempts())
                    .add("\nconnectionFailures", connectionFailures())
                    .add("\nclosedConnections", closedConnections())
                    .add("\nterminatedConnections", terminatedConnections())
                    .toString();
        }
    }
}

