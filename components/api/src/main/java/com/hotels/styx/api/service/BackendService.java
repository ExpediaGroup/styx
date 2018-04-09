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
package com.hotels.styx.api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.checkThatOriginsAreDistinct;
import static com.hotels.styx.api.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.api.service.HealthCheckConfig.noHealthCheck;
import static com.hotels.styx.api.service.StickySessionConfig.stickySessionDisabled;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Represents the configuration of an application (i.e. a backend service) that Styx can proxy to.
 */
@JsonDeserialize(builder = BackendService.Builder.class)
public final class BackendService implements Identifiable {
    public static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 1000;

    /**
     * A protocol used for the backend service. This can be either HTTP or HTTPS.
     */
    public enum Protocol {
        HTTP,
        HTTPS
    }

    private final Id id;
    private final String path;
    private final ConnectionPool.Settings connectionPoolSettings;
    private final Set<Origin> origins;
    private final HealthCheckConfig healthCheckConfig;
    private final StickySessionConfig stickySessionConfig;
    private final List<RewriteConfig> rewrites;
    private final int responseTimeoutMillis;
    private final TlsSettings tlsSettings;

    /**
     * Creates an Application builder.
     *
     * @return a new builder
     */
    public static Builder newBackendServiceBuilder() {
        return new Builder();
    }

    /**
     * Creates an Application builder that inherits from an existing Application.
     *
     * @param backendService application
     * @return a new builder
     */
    public static Builder newBackendServiceBuilder(BackendService backendService) {
        return new Builder(backendService);
    }

    private BackendService(Builder builder) {
        this.id = checkNotNull(builder.id, "id");
        this.path = checkNotNull(builder.path, "path");
        this.connectionPoolSettings = checkNotNull(builder.connectionPoolSettings);
        this.origins = ImmutableSet.copyOf(builder.origins);
        this.healthCheckConfig = checkNotNull(builder.healthCheckConfig);
        this.stickySessionConfig = checkNotNull(builder.stickySessionConfig);
        this.rewrites = checkNotNull(builder.rewrites);
        this.responseTimeoutMillis = builder.responseTimeoutMillis == 0
                ? DEFAULT_RESPONSE_TIMEOUT_MILLIS
                : builder.responseTimeoutMillis;
        this.tlsSettings = builder.tlsSettings;

        checkThatOriginsAreDistinct(origins);
        checkArgument(responseTimeoutMillis >= 0, "Request timeout must be greater than or equal to zero");
    }

    @Override
    public Id id() {
        return this.id;
    }

    @JsonProperty("id")
    String idAsString() {
        return this.id.toString();
    }

    @JsonProperty("path")
    public String path() {
        return this.path;
    }

    @JsonProperty("origins")
    public Set<Origin> origins() {
        return this.origins;
    }

    @JsonProperty("connectionPool")
    public ConnectionPool.Settings connectionPoolConfig() {
        return this.connectionPoolSettings;
    }

    @JsonProperty("healthCheck")
    public HealthCheckConfig healthCheckConfig() {
        return healthCheckConfig;
    }

    @JsonProperty("stickySession")
    public StickySessionConfig stickySessionConfig() {
        return this.stickySessionConfig;
    }

    @JsonProperty("rewrites")
    public List<RewriteConfig> rewrites() {
        return this.rewrites;
    }

    @JsonProperty("responseTimeoutMillis")
    public int responseTimeoutMillis() {
        return this.responseTimeoutMillis;
    }

    public Optional<TlsSettings> tlsSettings() {
        return Optional.ofNullable(this.tlsSettings);
    }

    @JsonProperty("tlsSettings")
    private TlsSettings getTlsSettings() {
        return this.tlsSettings().orElse(null);
    }

    public Protocol protocol() {
        if (tlsSettings == null) {
            return Protocol.HTTP;
        } else {
            return Protocol.HTTPS;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, path, connectionPoolSettings, origins,
                healthCheckConfig, stickySessionConfig, rewrites, responseTimeoutMillis);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BackendService other = (BackendService) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.path, other.path)
                && Objects.equals(this.connectionPoolSettings, other.connectionPoolSettings)
                && Objects.equals(this.origins, other.origins)
                && Objects.equals(this.healthCheckConfig, other.healthCheckConfig)
                && Objects.equals(this.stickySessionConfig, other.stickySessionConfig)
                && Objects.equals(this.rewrites, other.rewrites)
                && Objects.equals(this.tlsSettings, other.tlsSettings)
                && Objects.equals(this.responseTimeoutMillis, other.responseTimeoutMillis);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", this.id)
                .add("path", this.path)
                .add("origins", this.origins)
                .add("connectionPoolSettings", this.connectionPoolSettings)
                .add("healthCheckConfig", this.healthCheckConfig)
                .add("stickySessionConfig", this.stickySessionConfig)
                .add("rewrites", this.rewrites)
                .add("tlsSettings", this.tlsSettings)
                .toString();
    }

    public Builder newCopy() {
        return new Builder(this);
    }

    /**
     * Application builder.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static final class Builder {
        private Id id = GENERIC_APP;
        private String path = "/";
        private Set<Origin> origins = emptySet();
        private ConnectionPool.Settings connectionPoolSettings = defaultConnectionPoolSettings();
        private StickySessionConfig stickySessionConfig = stickySessionDisabled();
        private HealthCheckConfig healthCheckConfig = noHealthCheck();
        private List<RewriteConfig> rewrites = emptyList();
        public int responseTimeoutMillis = DEFAULT_RESPONSE_TIMEOUT_MILLIS;
        private TlsSettings tlsSettings;

        public Builder() {
        }

        private Builder(BackendService backendService) {
            this.id = backendService.id;
            this.path = backendService.path;
            this.origins = backendService.origins;
            this.connectionPoolSettings = backendService.connectionPoolSettings;
            this.stickySessionConfig = backendService.stickySessionConfig;
            this.healthCheckConfig = backendService.healthCheckConfig;
            this.rewrites = backendService.rewrites;
            this.responseTimeoutMillis = backendService.responseTimeoutMillis;
            this.tlsSettings = backendService.tlsSettings().orElse(null);
        }

        /**
         * Adds an ID.
         *
         * @param id an ID
         * @return this builder
         */
        public Builder id(Id id) {
            this.id = checkNotNull(id);
            return this;
        }

        /**
         * Sets an ID.
         *
         * @param id an ID
         * @return this builder
         */
        @JsonProperty("id")
        public Builder id(String id) {
            return id(Id.id(id));
        }

        /**
         * Sets a path.
         *
         * @param path a path
         * @return this builder
         */
        @JsonProperty("path")
        public Builder path(String path) {
            this.path = checkValidPath(checkNotNull(path));
            return this;
        }

        private String checkValidPath(String path) {
            try {
                URI.create(path);
                return path;
            } catch (Throwable cause) {
                String message = format("Invalid path. Path='%s'", path);
                throw new IllegalArgumentException(message, cause);
            }
        }

        /**
         * Sets the response timeout in milliseconds.
         *
         * @param timeout a response timeout in milliseconds.
         * @return this builder
         */
        @JsonProperty("responseTimeoutMillis")
        public Builder responseTimeoutMillis(int timeout) {
            this.responseTimeoutMillis = timeout;
            return this;
        }

        /**
         * Sets hosts.
         *
         * @param origins origins
         * @return this builder
         */
        @JsonProperty("origins")
        public Builder origins(Set<Origin> origins) {
            this.origins = checkNotNull(origins);
            return this;
        }

        /**
         * Sets the https settings.
         * For Jackson JSON serialiser that de-serialises from Option<TlsSettings>.
         */
        Builder https(Optional<TlsSettings> tlsSettings) {
            this.tlsSettings = tlsSettings.orElse(null);
            return this;
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        @JsonProperty("sslSettings")
        public Builder httpsOld(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        @JsonProperty("tlsSettings")
        public Builder https(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        /**
         * Sets hosts.
         *
         * @param origins origins
         * @return this builder
         */
        public Builder origins(Origin... origins) {
            return origins(ImmutableSet.copyOf(origins));
        }

        /**
         * Sets rewrites to be performed on URLs.
         *
         * @param rewriteConfigs rewrite configuration
         * @return this builder
         */
        public Builder rewrites(RewriteConfig... rewriteConfigs) {
            return rewrites(asList(rewriteConfigs));
        }

        /**
         * Sets rewrites to be performed on URLs.
         *
         * @param rewriteConfigs rewrite configuration
         * @return this builder
         */
        @JsonProperty("rewrites")
        public Builder rewrites(List<RewriteConfig> rewriteConfigs) {
            this.rewrites = ImmutableList.copyOf(rewriteConfigs);
            return this;
        }

        /**
         * Sets connection pool configuration.
         *
         * @param connectionPoolSettings connection pool configuration
         * @return this builder
         */
        @JsonProperty("connectionPool")
        public Builder connectionPoolConfig(ConnectionPoolSettings connectionPoolSettings) {
            this.connectionPoolSettings = checkNotNull(connectionPoolSettings);
            return this;
        }

        /**
         * Sets connection pool configuration.
         *
         * @param connectionPoolSettings connection pool configuration
         * @return this builder
         */
        public Builder connectionPoolConfig(ConnectionPool.Settings connectionPoolSettings) {
            this.connectionPoolSettings = checkNotNull(connectionPoolSettings);
            return this;
        }

        /**
         * Sets sticky-session configuration.
         *
         * @param stickySessionConfig sticky-session configuration.
         * @return this builder
         */
        @JsonProperty("stickySession")
        public Builder stickySessionConfig(StickySessionConfig stickySessionConfig) {
            this.stickySessionConfig = checkNotNull(stickySessionConfig);
            return this;
        }

        /**
         * Sets health-check configuration.
         *
         * @param healthCheckConfig health-check configuration
         * @return this builder
         */
        @JsonProperty("healthCheck")
        public Builder healthCheckConfig(HealthCheckConfig healthCheckConfig) {
            this.healthCheckConfig = checkNotNull(healthCheckConfig);
            return this;
        }

        /**
         * Builds the application.
         *
         * @return the application
         */
        public BackendService build() {
            return new BackendService(this);
        }
    }
}
