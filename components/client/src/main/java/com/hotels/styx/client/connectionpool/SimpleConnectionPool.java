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

    private final ConnectionPoolSettings poolSettings;
    private final ConnectionSettings connectionSettings;
    private final Connection.Factory connectionFactory;
    private final Origin origin;

    private final ConcurrentLinkedDeque<MonoSink<Connection>> waitingSubscribers;
    private final Queue<Connection> activeConnections;
    private final AtomicInteger borrowedCount = new AtomicInteger();
    private final SimpleConnectionPool.ConnectionPoolStats stats = new SimpleConnectionPool.ConnectionPoolStats();
    private final AtomicInteger connectionAttempts = new AtomicInteger();
    private final AtomicInteger closedConnections = new AtomicInteger();
    private final AtomicInteger terminatedConnections = new AtomicInteger();
    private final AtomicInteger connectionFailures = new AtomicInteger();


    public SimpleConnectionPool(Origin origin, ConnectionPoolSettings poolSettings, Connection.Factory connectionFactory) {
        this.origin = requireNonNull(origin);
        this.poolSettings = requireNonNull(poolSettings);
        this.connectionSettings = new ConnectionSettings(poolSettings.connectTimeoutMillis());
        this.connectionFactory = requireNonNull(connectionFactory);
        this.activeConnections = new ConcurrentLinkedDeque<>();
        this.waitingSubscribers = new ConcurrentLinkedDeque<>();
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Publisher<Connection> borrowConnection() {
        return Mono.<Connection>create(sink -> {
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
        }).timeout(
                Duration.ofMillis(poolSettings.pendingConnectionTimeoutMillis()),
                Mono.error(new MaxPendingConnectionTimeoutException(origin, connectionSettings.connectTimeoutMillis())));
    }

    private void newConnection() {
        connectionAttempts.incrementAndGet();
        newConnection(3)
                .doOnNext(it -> it.addConnectionListener(SimpleConnectionPool.this))
                .subscribe(
                        this::queueNewConnection,
                        cause -> connectionFailures.incrementAndGet()
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
        activeConnections.remove(connection);
    }

    public ConnectionPool.Stats stats() {
        return this.stats;
    }

    @VisibleForTesting
    private class ConnectionPoolStats implements Stats {
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
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("\nactiveConnections", availableConnectionCount())
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

