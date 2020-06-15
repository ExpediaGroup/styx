/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.hotels.styx.api.Metrics.name;
import static com.hotels.styx.api.Metrics.APPID_TAG;
import static com.hotels.styx.api.Metrics.ORIGINID_TAG;

class StatsReportingConnectionPool implements ConnectionPool {
    private static final String PREFIX = "connectionspool";

    private final ConnectionPool connectionPool;
    private final MeterRegistry meterRegistry;
    private final Set<Meter> meters = new HashSet<>();
    private final Tags tags;

    public StatsReportingConnectionPool(ConnectionPool connectionPool, MeterRegistry meterRegistry) {
        this.connectionPool = connectionPool;
        this.meterRegistry = meterRegistry;
        this.tags = Tags.of(APPID_TAG, connectionPool.getOrigin().applicationId().toString(),
                ORIGINID_TAG, connectionPool.getOrigin().id().toString());
        registerMetrics();
    }

    @Override
    public Origin getOrigin() {
        return connectionPool.getOrigin();
    }

    @Override
    public Publisher<Connection> borrowConnection() {
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

    private void registerMetrics() {
        ConnectionPool.Stats stats = connectionPool.stats();
        registerGauge("busy-connections", stats::busyConnectionCount);
        registerGauge("pending-connections", stats::pendingConnectionCount);
        registerGauge("available-connections", stats::availableConnectionCount);
        registerGauge("connection-attempts", stats::connectionAttempts);
        registerGauge("connection-failures", stats::connectionFailures);
        registerGauge("connections-closed", stats::closedConnections);
        registerGauge("connections-terminated", stats::terminatedConnections);
        registerGauge("connections-in-establishment", stats::connectionsInEstablishment);
    }

    private void registerGauge(String name, Supplier<Number> supplier) {
        meters.add(Gauge.builder(name(PREFIX, name), supplier).tags(tags).register(meterRegistry));
    }

    private void removeMetrics() {
        meters.forEach(meterRegistry::remove);
        meters.clear();
    }
}
