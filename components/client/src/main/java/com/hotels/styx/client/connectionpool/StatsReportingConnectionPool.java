/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.Deleter;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

class StatsReportingConnectionPool implements ConnectionPool {
    private final ConnectionPool connectionPool;
    private final Set<Deleter> deleters = new HashSet<>();

    public StatsReportingConnectionPool(ConnectionPool connectionPool, CentralisedMetrics metrics) {
        this.connectionPool = requireNonNull(connectionPool);
        requireNonNull(metrics);

        Stats stats = this.connectionPool.stats();
        Origin origin = this.connectionPool.getOrigin();

        CentralisedMetrics.Proxy.Client clientMetrics = metrics.proxy().client();

        deleters.addAll(asList(
                clientMetrics.busyConnections(origin).register(stats::busyConnectionCount),
                clientMetrics.pendingConnections(origin).register(stats::pendingConnectionCount),
                clientMetrics.availableConnections(origin).register(stats::availableConnectionCount),
                clientMetrics.connectionAttempts(origin).register(stats::connectionAttempts),
                clientMetrics.connectionFailures(origin).register(stats::connectionFailures),
                clientMetrics.connectionsClosed(origin).register(stats::closedConnections),
                clientMetrics.connectionsTerminated(origin).register(stats::terminatedConnections),
                clientMetrics.connectionsInEstablishment(origin).register(stats::connectionsInEstablishment)
        ));
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

    private void removeMetrics() {
        deleters.forEach(Deleter::delete);
        deleters.clear();
    }
}
