/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.api.extension.service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

/**
 * Programmatically configurable connection pool settings.
 */
public class ConnectionPoolSettings {
    public static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 50;
    public static final int DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST = 25;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 11000;
    public static final long DEFAULT_CONNECTION_EXPIRATION_SECONDS = -1L;
    public static final Http2ConnectionPoolSettings DEFAULT_HTTP2_CONNECTION_POOL_SETTINGS = new Http2ConnectionPoolSettings();

    private final int maxConnectionsPerHost;
    private final int maxPendingConnectionsPerHost;
    private final int connectTimeoutMillis;
    private final int socketTimeoutMillis;
    private final int pendingConnectionTimeoutMillis;
    private final long connectionExpirationSeconds;
    private final Http2ConnectionPoolSettings http2ConnectionPoolSettings;

    ConnectionPoolSettings(Integer maxConnectionsPerHost,
                           Integer maxPendingConnectionsPerHost,
                           Integer connectTimeoutMillis,
                           @Deprecated Integer socketTimeoutMillis,
                           Integer pendingConnectionTimeoutMillis,
                           Long connectionExpirationSeconds,
                           Http2ConnectionPoolSettings http2ConnectionPoolSettings) {
        this.maxConnectionsPerHost = ofNullable(maxConnectionsPerHost).orElse(DEFAULT_MAX_CONNECTIONS_PER_HOST);
        this.maxPendingConnectionsPerHost = ofNullable(maxPendingConnectionsPerHost).orElse(DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST);
        this.connectTimeoutMillis = ofNullable(connectTimeoutMillis).orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        this.socketTimeoutMillis = ofNullable(socketTimeoutMillis).orElse(DEFAULT_SOCKET_TIMEOUT_MILLIS);
        this.pendingConnectionTimeoutMillis = ofNullable(pendingConnectionTimeoutMillis).orElse(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        this.connectionExpirationSeconds = ofNullable(connectionExpirationSeconds).orElse(DEFAULT_CONNECTION_EXPIRATION_SECONDS);
        this.http2ConnectionPoolSettings = ofNullable(http2ConnectionPoolSettings).orElse(DEFAULT_HTTP2_CONNECTION_POOL_SETTINGS);
    }

    public ConnectionPoolSettings(int maxConnectionsPerHost,
                           int maxPendingConnectionsPerHost,
                           int connectTimeoutMillis,
                           int pendingConnectionTimeoutMillis,
                           long connectionExpirationSeconds,
                           Http2ConnectionPoolSettings http2ConnectionPoolSettings) {
        this(maxConnectionsPerHost,
                maxPendingConnectionsPerHost,
                connectTimeoutMillis,
                DEFAULT_SOCKET_TIMEOUT_MILLIS,
                pendingConnectionTimeoutMillis,
                connectionExpirationSeconds,
                http2ConnectionPoolSettings);
    }

    private ConnectionPoolSettings(Builder builder) {
        this(
                builder.maxConnectionsPerHost,
                builder.maxPendingConnectionsPerHost,
                builder.connectTimeoutMillis,
                builder.socketTimeoutMillis,
                builder.pendingConnectionTimeoutMillis,
                builder.connectionExpirationSeconds,
                builder.http2ConnectionPoolSettings
        );
    }

    /**
     * Creates a new instance with default settings.
     *
     * @return a new instance
     */
    public static ConnectionPoolSettings defaultConnectionPoolSettings() {
        return new ConnectionPoolSettings(new Builder());
    }

    /**
     * Returns a socket timeout, in milliseconds.
     *
     * This getter is deprecated. It just serves the purpose of
     * implementing an interface contract.
     *
     * @deprecated Due to be removed in a future release.
     *
     * @return a socket timeout in milliseconds.
     */
    @Deprecated
    public int socketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int maxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public int maxPendingConnectionsPerHost() {
        return maxPendingConnectionsPerHost;
    }

    public int pendingConnectionTimeoutMillis() {
        return pendingConnectionTimeoutMillis;
    }

    public long connectionExpirationSeconds() {
        return connectionExpirationSeconds;
    }
    public Http2ConnectionPoolSettings http2ConnectionPoolSettings() {
        return http2ConnectionPoolSettings;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxConnectionsPerHost, maxPendingConnectionsPerHost, connectTimeoutMillis,
                socketTimeoutMillis, pendingConnectionTimeoutMillis, http2ConnectionPoolSettings);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConnectionPoolSettings other = (ConnectionPoolSettings) obj;
        return Objects.equals(this.maxConnectionsPerHost, other.maxConnectionsPerHost)
                && Objects.equals(this.maxPendingConnectionsPerHost, other.maxPendingConnectionsPerHost)
                && Objects.equals(this.connectTimeoutMillis, other.connectTimeoutMillis)
                && Objects.equals(this.socketTimeoutMillis, other.socketTimeoutMillis)
                && Objects.equals(this.pendingConnectionTimeoutMillis, other.pendingConnectionTimeoutMillis)
                && Objects.equals(this.http2ConnectionPoolSettings, other.http2ConnectionPoolSettings);
    }

    @Override
    public String toString() {
        return new StringBuilder(160)
                .append(this.getClass().getSimpleName())
                .append("{maxConnectionsPerHost=")
                .append(maxConnectionsPerHost)
                .append(", maxPendingConnectionsPerHost=")
                .append(maxPendingConnectionsPerHost)
                .append(", connectTimeoutMillis=")
                .append(connectTimeoutMillis)
                .append(", socketTimeoutMillis=")
                .append(socketTimeoutMillis)
                .append(", pendingConnectionTimeoutMillis=")
                .append(pendingConnectionTimeoutMillis)
                .append(", http2ConnectionPoolSettings=")
                .append(http2ConnectionPoolSettings)
                .append('}')
                .toString();
    }

    /**
     * A builder that builds {@link ConnectionPoolSettings}s. Will use default values for any settings not set.
     */
    public static final class Builder {
        private int maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
        private int maxPendingConnectionsPerHost = DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST;
        private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private int socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MILLIS;
        private int pendingConnectionTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private long connectionExpirationSeconds = DEFAULT_CONNECTION_EXPIRATION_SECONDS;
        private Http2ConnectionPoolSettings http2ConnectionPoolSettings = DEFAULT_HTTP2_CONNECTION_POOL_SETTINGS;

        /**
         * Constructs an instance with default settings.
         */
        public Builder() {
        }

        /**
         * Constructs an instance with inherited settings.
         *
         * @param settings settings to inherit from
         */
        public Builder(ConnectionPoolSettings settings) {
            this.maxConnectionsPerHost = settings.maxConnectionsPerHost();
            this.maxPendingConnectionsPerHost = settings.maxPendingConnectionsPerHost();
            this.connectTimeoutMillis = settings.connectTimeoutMillis();
            this.socketTimeoutMillis = settings.socketTimeoutMillis();
            this.pendingConnectionTimeoutMillis = settings.pendingConnectionTimeoutMillis();
            this.connectionExpirationSeconds = settings.connectionExpirationSeconds();
            this.http2ConnectionPoolSettings = settings.http2ConnectionPoolSettings();
        }

        /**
         * Sets the maximum number of active connections for a single hosts's connection pool.
         *
         * @param maxConnectionsPerHost maximum number of active connections
         * @return this builder
         */
        public Builder maxConnectionsPerHost(int maxConnectionsPerHost) {
            this.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        /**
         * Sets the maximum allowed number of consumers, per host, waiting for a connection.
         *
         * @param maxPendingConnectionsPerHost maximum number of consumers
         * @return this builder
         */
        public Builder maxPendingConnectionsPerHost(int maxPendingConnectionsPerHost) {
            this.maxPendingConnectionsPerHost = maxPendingConnectionsPerHost;
            return this;
        }

        /**
         * Sets socket read timeout.
         *
         * @deprecated Due to be removed in a future release.
         *
         * @param socketTimeout read timeout
         * @param timeUnit      unit of timeout
         * @return this builder
         */
        @Deprecated
        public Builder socketTimeout(int socketTimeout, TimeUnit timeUnit) {
            this.socketTimeoutMillis = (int) timeUnit.toMillis(socketTimeout);
            return this;
        }

        /**
         * Sets socket connect timeout.
         *
         * @param connectTimeout connect timeout
         * @param timeUnit       unit of timeout
         * @return this builder
         */
        public Builder connectTimeout(int connectTimeout, TimeUnit timeUnit) {
            this.connectTimeoutMillis = (int) timeUnit.toMillis(connectTimeout);
            return this;
        }

        /**
         * Sets the maximum wait time for pending consumers.
         *
         * @param waitTimeout timeout
         * @param timeUnit    unit that timeout is measured in
         * @return this builder
         */
        public Builder pendingConnectionTimeout(int waitTimeout, TimeUnit timeUnit) {
            this.pendingConnectionTimeoutMillis = (int) timeUnit.toMillis(waitTimeout);
            return this;
        }

        /**
         * Sets the expiration time on a connection, after which the connection should be terminated.
         *
         * @param connectionExpirationSeconds connection viability
         * @return this builder
         */
        public Builder connectionExpirationSeconds(long connectionExpirationSeconds) {
            this.connectionExpirationSeconds = connectionExpirationSeconds;
            return this;
        }

        /**
         * Sets the connection pool settings for HTTP/2.
         *
         * @param http2ConnectionPoolSettings connection pool settings for HTTP/2
         * @return this builder
         */
        public Builder http2ConnectionPoolSettings(Http2ConnectionPoolSettings http2ConnectionPoolSettings) {
            this.http2ConnectionPoolSettings = http2ConnectionPoolSettings;
            return this;
        }

        /**
         * Constructs a new instance with the configured settings.
         *
         * @return a new instance
         */
        public ConnectionPoolSettings build() {
            return new ConnectionPoolSettings(this);
        }
    }
}
