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

import com.codahale.metrics.Gauge;
import com.hotels.styx.client.Connection;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import org.slf4j.Logger;
import rx.Observable;

import static com.hotels.styx.client.applications.metrics.OriginMetrics.originMetricsScope;
import static java.util.Arrays.asList;
import static org.slf4j.LoggerFactory.getLogger;

class StatsReportingConnectionPool implements ConnectionPool {
    private static final String METRICS_NAME = "connectionspool";
    private static final Logger LOGGER = getLogger(StatsReportingConnectionPool.class);

    private final ConnectionPool connectionPool;
    private final MetricRegistry metricRegistry;

    public StatsReportingConnectionPool(ConnectionPool connectionPool, MetricRegistry metricRegistry) {
        this.connectionPool = connectionPool;
        this.metricRegistry = metricRegistry;
        registerMetrics();
    }

    @Override
    public Origin getOrigin() {
        return connectionPool.getOrigin();
    }

    @Override
    public Observable borrowConnection() {
        return connectionPool.borrowConnection();
    }

    @Override
    public boolean returnConnection(Connection connection) {
        return connectionPool.returnConnection(connection);
    }

    @Override
    public boolean closeConnection(Connection connection) {
        return connectionPool.closeConnection(connection);
    }

    @Override
    public boolean isExhausted() {
        return connectionPool.isExhausted();
    }

    @Override
    public Stats stats() {
        return connectionPool.stats();
    }

    @Override
    public ConnectionPoolSettings settings() {
        return connectionPool.settings();
    }

    @Override
    public void close() {
        connectionPool.close();
        removeMetrics();
    }

    private void removeMetrics() {
        MetricRegistry scopedRegistry = getMetricScope(connectionPool);
        asList("busy-connections", "pending-connections", "available-connections", "ttfb",
                "connection-attempts", "connection-failures", "connections-closed", "connections-terminated")
                .forEach(scopedRegistry::deregister);
    }

    private void registerMetrics() {
        MetricRegistry scopedRegistry = getMetricScope(connectionPool);
        try {
            registerMetrics(connectionPool, scopedRegistry);
        } catch (IllegalArgumentException e) {
            // metrics already registered.
            LOGGER.debug("IllegalArgumentException when registering metrics with CodaHale: {}", e);
        }
    }

    private void registerMetrics(ConnectionPool hostConnectionPool, MetricRegistry scopedRegistry) {
        ConnectionPool.Stats stats = hostConnectionPool.stats();
        scopedRegistry.register("busy-connections", (Gauge<Integer>) stats::busyConnectionCount);
        scopedRegistry.register("pending-connections", (Gauge<Integer>) stats::pendingConnectionCount);
        scopedRegistry.register("available-connections", (Gauge<Integer>) stats::availableConnectionCount);
        scopedRegistry.register("ttfb", (Gauge<Integer>) () -> (int) stats.timeToFirstByteMs());
        scopedRegistry.register("connection-attempts", (Gauge<Integer>) () -> (int) stats.connectionAttempts());
        scopedRegistry.register("connection-failures", (Gauge<Integer>) () -> (int) stats.connectionFailures());
        scopedRegistry.register("connections-closed", (Gauge<Integer>) () -> (int) stats.closedConnections());
        scopedRegistry.register("connections-terminated", (Gauge<Integer>) () -> (int) stats.terminatedConnections());
    }

    private MetricRegistry getMetricScope(ConnectionPool hostConnectionPool) {
        return this.metricRegistry.scope(com.codahale.metrics.MetricRegistry.name(originMetricsScope(hostConnectionPool.getOrigin()), METRICS_NAME));
    }
}
