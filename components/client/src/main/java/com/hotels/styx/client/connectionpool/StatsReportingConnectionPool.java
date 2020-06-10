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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.stream.Stream;

import static com.hotels.styx.api.Metrics.name;
import static com.hotels.styx.api.metrics.CommonTags.APPID;
import static com.hotels.styx.api.metrics.CommonTags.ORIGINID;

class StatsReportingConnectionPool implements ConnectionPool {
    private static final String PREFIX = "connectionspool";

    private final ConnectionPool connectionPool;
    private final MeterRegistry meterRegistry;
    private final Tags tags;

    public StatsReportingConnectionPool(ConnectionPool connectionPool, MeterRegistry meterRegistry) {
        this.connectionPool = connectionPool;
        this.meterRegistry = meterRegistry;
        this.tags = Tags.of(APPID, connectionPool.getOrigin().applicationId().toString(),
                ORIGINID, connectionPool.getOrigin().id().toString());
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
        meterRegistry.gauge(name(PREFIX, "busy-connections"), tags, stats, Stats::busyConnectionCount);
        meterRegistry.gauge(name(PREFIX, "pending-connections"), tags, stats, Stats::pendingConnectionCount);
        meterRegistry.gauge(name(PREFIX, "available-connections"), tags, stats, Stats::availableConnectionCount);
        meterRegistry.gauge(name(PREFIX, "connection-attempts"), tags, stats, Stats::connectionAttempts);
        meterRegistry.gauge(name(PREFIX, "connection-failures"), tags, stats, Stats::connectionFailures);
        meterRegistry.gauge(name(PREFIX, "connections-closed"), tags, stats, Stats::closedConnections);
        meterRegistry.gauge(name(PREFIX, "connections-terminated"), tags, stats, Stats::terminatedConnections);
        meterRegistry.gauge(name(PREFIX, "connections-in-establishment"), tags, stats, Stats::connectionsInEstablishment);
    }

    private void removeMetrics() {
        Stream.of("busy-connections", "pending-connections", "available-connections",
                "connection-attempts", "connection-failures", "connections-closed", "connections-terminated",
                "connections-in-establishment")
                .map(n -> meterRegistry.find(name(PREFIX, n)).tags(tags).gauge())
                .filter(Objects::nonNull)
                .forEach(meterRegistry::remove);
    }
}
