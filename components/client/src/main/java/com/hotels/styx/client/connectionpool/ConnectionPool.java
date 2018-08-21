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

import com.hotels.styx.client.Connection;
import com.hotels.styx.api.extension.Origin;
import rx.Observable;

import java.io.Closeable;
import java.util.function.Function;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;

/**
 * A pool of connections.
 */
public interface ConnectionPool extends Closeable {

    /**
     * An object that provides statistics relating to connection pooling.
     */
    interface Stats {
        /**
         * Return the number of connections currently borrowed.
         *
         * @return number of busy connections
         */
        int busyConnectionCount();

        /**
         * Returns the number of connections that can be borrowed immediately without having to make a new connection to
         * the remote server.
         *
         * @return number of available connections
         */
        int availableConnectionCount();

        /**
         * Return the number of pending connection open attempts.
         *
         * @return number of pending connections
         */
        int pendingConnectionCount();

        /**
         * Returns the mean time to first byte in milliseconds.
         *
         * @return time in milliseconds
         */
        long timeToFirstByteMs();

        /**
         * Number of connections establishment attempts that have been initiated from the
         * connection pool.
         *
         * @return number of connection establishment attempts
         */
        int connectionAttempts();

        /**
         * Counts the number of failed connection establishment attempts. To calculate number of successful
         * connection establishment attempts, take away `failed-connection-attempts` from  value of
         * `connection-attempts`.
         *
         * @return number of failed connection attempts
         */
        int connectionFailures();

        /**
         * Number of connections closed by Styx, for whatever reason.
         *
         * @return Number of connections closed by Styx.
         */
        int closedConnections();

        /**
         * Number of terminated connections, for whatever reason. Includes those connection closures initiated by Styx.
         * To work out number of connections terminated by an origin, take away value of `closed-connections` counter
         * from `terminated-connections`.
         *
         * @return
         */
        int terminatedConnections();
    }

    /**
     * Factory that creates connection pools for given origins.
     */
    interface Factory {
        /**
         * Create a connection pool for the given origin.
         *
         * @param origin origin to connect to
         * @return connection pool for origin
         */
        ConnectionPool create(Origin origin);
    }

    /**
     * Return the origin that connections will be connected to.
     *
     * @return an origin
     */
    Origin getOrigin();

    /**
     * Borrow a connection from the host. May create a new connection if one is
     * not available, the borrowed connection must either be returned by calling
     * {@link ConnectionPool#returnConnection}
     * or closed by {@link ConnectionPool#closeConnection}.
     *
     * @return the borrowed connection
     */
    Observable<Connection> borrowConnection();

    /**
     * Returns back the connection to the host's pool. May close the connection if the
     * pool is down or the last exception on the connection is determined to be
     * fatal.
     *
     * @param connection the connection to be returned
     * @return true if connection was closed
     */
    boolean returnConnection(Connection connection);

    /**
     * Close the connection and update internal state.
     *
     * @param connection connection
     * @return true if connection was closed
     */
    boolean closeConnection(Connection connection);

    /**
     * Returns true if this pool is exhausted, i.e. if the maximum number of active connections has been reached.
     *
     * @return true if this pool is exhausted
     */
    boolean isExhausted();

    /**
     * Returns a current snapshot of this pool's cumulative statistics.
     * All stats are initialized to zero, and are monotonically increasing over the lifetime of the pool.
     *
     * @return a Stats object
     */
    Stats stats();

    /**
     * The settings used to configure the pool.
     *
     * @return the pool settings
     */
    ConnectionPoolSettings settings();

    default <T> Observable<T> withConnection(Function<Connection, Observable<T>> task) {
        return borrowConnection()
                .flatMap(connection ->
                        task.apply(connection)
                                .doOnCompleted(() -> returnConnection(connection))
                                .doOnError(throwable -> closeConnection(connection)));
    }

    /**
     * Closes this pool and releases any system resources associated with it.
     */
    default void close() {

    }
}
