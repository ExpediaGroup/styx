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
package com.hotels.styx.proxy.backends.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.ConnectionPoolSettings;
import com.hotels.styx.api.service.HealthCheckConfig;
import com.hotels.styx.api.service.RewriteConfig;
import com.hotels.styx.api.service.StickySessionConfig;
import com.hotels.styx.api.service.TlsSettings;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.service.BackendService.DEFAULT_RESPONSE_TIMEOUT_MILLIS;
import static com.hotels.styx.api.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.api.service.HealthCheckConfig.noHealthCheck;
import static com.hotels.styx.api.service.StickySessionConfig.stickySessionDisabled;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Used to provide annotated properties for fields of {@link BackendService} object, required to keep naming of fields
 * used in configuration on deserialization.
 */
@JsonDeserialize(builder = BackendServiceDeserializer.Builder.class)
public final class BackendServiceDeserializer {


    private final BackendService backendService;

    private BackendServiceDeserializer(BackendService.Builder backendServiceBuilder) {
        this.backendService = backendServiceBuilder.build();
    }

    public BackendService backendService() {
        return backendService;
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
            this.id = backendService.id();
            this.path = backendService.path();
            this.origins = backendService.origins();
            this.connectionPoolSettings = backendService.connectionPoolConfig();
            this.stickySessionConfig = backendService.stickySessionConfig();
            this.healthCheckConfig = backendService.healthCheckConfig();
            this.rewrites = backendService.rewrites();
            this.responseTimeoutMillis = backendService.responseTimeoutMillis();
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
        public BackendServiceDeserializer build() {
            BackendService.Builder builder = BackendService.newBackendServiceBuilder();
            builder.id(this.id);
            builder.path(this.path);
            builder.origins(this.origins);
            builder.connectionPoolConfig(this.connectionPoolSettings);
            builder.healthCheckConfig(this.healthCheckConfig);
            builder.stickySessionConfig(this.stickySessionConfig);
            builder.rewrites(this.rewrites);
            builder.responseTimeoutMillis(this.responseTimeoutMillis);
            builder.https(this.tlsSettings);
            builder.httpsOld(this.tlsSettings);
            return new BackendServiceDeserializer(builder);
        }
    }
}


