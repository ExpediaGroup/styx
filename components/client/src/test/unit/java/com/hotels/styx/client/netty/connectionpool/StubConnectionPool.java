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
package com.hotels.styx.client.netty.connectionpool;

import com.google.common.base.Objects;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import rx.Observable;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static rx.Observable.just;

public class StubConnectionPool implements ConnectionPool, Comparable<ConnectionPool> {
    private Connection connection;
    private final Origin origin;

    private int busyConnectionCount = 0;
    private long timeToFirstByte = 0;
    private int maxConnectionsPerHost = 1;
    private int availableConnections = 0;
    private final ConnectionPoolSettings settings;
    private int pendingConnectionCount;

    public StubConnectionPool(Origin origin) {
        this(new StubConnectionFactory.StubConnection(origin));
    }

    public StubConnectionPool(Connection connection) {
        this.connection = connection;
        this.origin = connection.getOrigin();
        this.settings = defaultConnectionPoolSettings();
    }

    public StubConnectionPool(Origin origin_one, ConnectionPoolSettings settings) {
        this.origin = origin_one;
        this.settings = settings;
    }

    @Override
    public int compareTo(ConnectionPool other) {
        return this.connection.getOrigin().host().toString().compareTo(other.getOrigin().host().toString());
    }

    @Override
    public Origin getOrigin() {
        return this.origin;
    }

    @Override
    public Observable<Connection> borrowConnection() {
        return just(connection);
    }

    @Override
    public boolean returnConnection(Connection connection) {
        return false;
    }

    @Override
    public boolean closeConnection(Connection connection) {
        return false;
    }

    @Override
    public boolean isExhausted() {
        return false;
    }

    @Override
    public Stats stats() {
        return new Stats() {
            @Override
            public int pendingConnectionCount() {
                return pendingConnectionCount;
            }

            @Override
            public int availableConnectionCount() {
                return availableConnections;
            }

            @Override
            public int busyConnectionCount() {
                return busyConnectionCount;
            }

            @Override
            public long timeToFirstByteMs() {
                return timeToFirstByte;
            }

            @Override
            public int connectionAttempts() {
                return 0;
            }

            @Override
            public int connectionFailures() {
                return 0;
            }

            @Override
            public int closedConnections() {
                return 0;
            }

            @Override
            public int terminatedConnections() {
                return 0;
            }
        };
    }

    @Override
    public ConnectionPoolSettings settings() {
        return settings;
    }

    public StubConnectionPool withBusyConnections(int busyConnectionCount) {
        this.busyConnectionCount = busyConnectionCount;
        return this;
    }

    public ConnectionPool withPendingConnections(int pendingConnectionCount) {
        this.pendingConnectionCount = pendingConnectionCount;
        return this;
    }

    public StubConnectionPool withTimeToFirstByte(long timeToFirstByte) {
        this.timeToFirstByte = timeToFirstByte;
        return this;
    }

    public StubConnectionPool withMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        return this;
    }

    public StubConnectionPool withAvailableConnections(int availableConnections) {
        this.availableConnections = availableConnections;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(origin, busyConnectionCount, pendingConnectionCount, timeToFirstByte);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StubConnectionPool other = (StubConnectionPool) obj;
        return Objects.equal(this.origin, other.origin) &&
                Objects.equal(this.busyConnectionCount, other.busyConnectionCount) &&
                Objects.equal(this.pendingConnectionCount, other.pendingConnectionCount) &&
                Objects.equal(this.timeToFirstByte, other.timeToFirstByte);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("origin", origin)
                .add("busyConnections", busyConnectionCount)
                .add("pendingConnections", pendingConnectionCount)
                .add("timeToFirstByte", timeToFirstByte)
                .toString();
    }
}
