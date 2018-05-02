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

import com.google.common.base.Objects;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionDestination;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.service.TlsSettings;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.client.HttpRequestOperationFactory;
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import rx.Observable;

import java.util.function.Function;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static rx.Observable.error;

/**
 * An implementation of {@link ConnectionDestination} that creates new connections and closes them after use.
 */
public class CloseAfterUseConnectionDestination implements ConnectionDestination, Comparable<ConnectionDestination>, Connection.Listener {
    private final Origin origin;

    private final Connection.Settings connectionSettings;
    private final Connection.Factory connectionFactory;

    /**
     * Constructs an instance.
     *
     * @param origin             origin to connect to
     * @param connectionSettings connection settings
     * @param connectionFactory  connection factory
     */
    CloseAfterUseConnectionDestination(Origin origin, Connection.Settings connectionSettings, Connection.Factory connectionFactory) {
        this.connectionSettings = checkNotNull(connectionSettings);
        this.origin = checkNotNull(origin);
        this.connectionFactory = checkNotNull(connectionFactory);
    }

    @Override
    public int compareTo(ConnectionDestination other) {
        return this.origin.compareTo(other.getOrigin());
    }

    @Override
    public <T> Observable<T> withConnection(Function<Connection, Observable<T>> task) {
        return createConnection()
                .flatMap(connection -> execute(task, connection)
                        .doOnCompleted(connection::close)
                        .doOnError(throwable -> connection.close()));
    }

    private static <T> Observable<T> execute(Function<Connection, Observable<T>> task, Connection connection) {
        try {
            return task.apply(connection);
        } catch (Throwable t) {
            return error(t);
        }
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    private Observable<Connection> createConnection() {
        return connectionFactory.createConnection(origin, connectionSettings)
                .doOnNext(connection -> connection.addConnectionListener(CloseAfterUseConnectionDestination.this));
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
        CloseAfterUseConnectionDestination other = (CloseAfterUseConnectionDestination) obj;
        return Objects.equal(this.origin, other.origin);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("origin", origin)
                .toString();
    }

    @Override
    public void connectionClosed(Connection connection) {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public Connection.Settings settings() {
        return connectionSettings;
    }

    /**
     * Factory to construct instances.
     */
    public static class Factory implements ConnectionDestination.Factory {
        private Connection.Settings connectionSettings = new ConnectionSettings(1000);
        private Connection.Factory connectionFactory;
        private TlsSettings tlsSettings;
        private HttpRequestOperationFactory requestOperationFactory = request -> new HttpRequestOperation(
                request,
                null,
                false,
                60000,
                false,
                false);

        public Factory connectionSettings(Connection.Settings connectionSettings) {
            this.connectionSettings = connectionSettings;
            return this;
        }

        public Factory connectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Factory tlsSettings(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        @Override
        public CloseAfterUseConnectionDestination create(Origin origin) {
            Connection.Factory connectionFactory = this.connectionFactory != null
                    ? this.connectionFactory
                    : new NettyConnectionFactory.Builder()
                    .name("styx-client")
                    .tlsSettings(tlsSettings)
                    .httpRequestOperationFactory(requestOperationFactory)
                    .build();

            return new CloseAfterUseConnectionDestination(origin, connectionSettings, connectionFactory);
        }
    }
}

