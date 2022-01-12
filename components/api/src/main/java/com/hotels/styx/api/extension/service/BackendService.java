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
package com.hotels.styx.api.extension.service;

import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.extension.Origin;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.extension.Origin.checkThatOriginsAreDistinct;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.api.extension.service.StickySessionConfig.stickySessionDisabled;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

/**
 * Represents the configuration of an application (i.e. a backend service) that Styx can proxy to.
 */
public final class BackendService implements Identifiable {
    public static final int DEFAULT_RESPONSE_TIMEOUT_MILLIS = 1000;
    public static final int USE_DEFAULT_MAX_HEADER_SIZE = 0;

    /**
     * A protocol used for the backend service. This can be either HTTP or HTTPS.
     */
    public enum Protocol {
        HTTP,
        HTTPS
    }

    private final Id id;
    private final String path;
    private final ConnectionPoolSettings connectionPoolSettings;
    private final Set<Origin> origins;
    private final HealthCheckConfig healthCheckConfig;
    private final StickySessionConfig stickySessionConfig;
    private final List<RewriteConfig> rewrites;
    private final boolean overrideHostHeader;
    private final int responseTimeoutMillis;
    private final int maxHeaderSize;
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
        this.id = requireNonNull(builder.id, "id");
        this.path = requireNonNull(builder.path, "path");
        this.connectionPoolSettings = requireNonNull(builder.connectionPoolSettings);
        this.origins = Set.copyOf(builder.origins);
        this.healthCheckConfig = nullIfDisabled(builder.healthCheckConfig);
        this.stickySessionConfig = requireNonNull(builder.stickySessionConfig);
        this.rewrites = requireNonNull(builder.rewrites);
        this.responseTimeoutMillis = builder.responseTimeoutMillis == 0
                ? DEFAULT_RESPONSE_TIMEOUT_MILLIS
                : builder.responseTimeoutMillis;
        this.tlsSettings = builder.tlsSettings;
        this.maxHeaderSize = builder.maxHeaderSize;
        this.overrideHostHeader = builder.overrideHostHeader;

        checkThatOriginsAreDistinct(origins);
        if (responseTimeoutMillis < 0) {
            throw new IllegalArgumentException("Request timeout must be greater than or equal to zero");
        }
    }

    private static HealthCheckConfig nullIfDisabled(HealthCheckConfig healthCheckConfig) {
        return healthCheckConfig != null && healthCheckConfig.isEnabled()
                ? healthCheckConfig
                : null;
    }

    @Override
    public Id id() {
        return this.id;
    }

    String idAsString() {
        return this.id.toString();
    }

    public String path() {
        return this.path;
    }

    public Set<Origin> origins() {
        return this.origins;
    }

    public ConnectionPoolSettings connectionPoolConfig() {
        return this.connectionPoolSettings;
    }

    public HealthCheckConfig healthCheckConfig() {
        return healthCheckConfig;
    }

    public StickySessionConfig stickySessionConfig() {
        return this.stickySessionConfig;
    }

    public List<RewriteConfig> rewrites() {
        return this.rewrites;
    }

    public int responseTimeoutMillis() {
        return this.responseTimeoutMillis;
    }

    public int maxHeaderSize() {
        return this.maxHeaderSize;
    }

    public boolean isOverrideHostHeader() {
        return this.overrideHostHeader;
    }

    public Optional<TlsSettings> tlsSettings() {
        return Optional.ofNullable(this.tlsSettings);
    }

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
                healthCheckConfig, stickySessionConfig, rewrites,
                responseTimeoutMillis, maxHeaderSize);
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
                && Objects.equals(this.responseTimeoutMillis, other.responseTimeoutMillis)
                && Objects.equals(this.maxHeaderSize, other.maxHeaderSize);
    }

    @Override
    public String toString() {
        return new StringBuilder(128)
                .append(this.getClass().getSimpleName())
                .append("{id=")
                .append(id)
                .append(", path=")
                .append(path)
                .append(", origins=")
                .append(origins)
                .append(", connectionPoolSettings=")
                .append(connectionPoolSettings)
                .append(", healthCheckConfig=")
                .append(healthCheckConfig)
                .append(", stickySessionConfig=")
                .append(stickySessionConfig)
                .append(", rewrites=")
                .append(rewrites)
                .append(", tlsSettings=")
                .append(tlsSettings)
                .append('}')
                .toString();
    }

    public Builder newCopy() {
        return new Builder(this);
    }

    /**
     * Application builder.
     */
    public static final class Builder {
        private Id id = GENERIC_APP;
        private String path = "/";
        private Set<Origin> origins = emptySet();
        private ConnectionPoolSettings connectionPoolSettings = defaultConnectionPoolSettings();
        private StickySessionConfig stickySessionConfig = stickySessionDisabled();
        private HealthCheckConfig healthCheckConfig;
        private List<RewriteConfig> rewrites = emptyList();
        private boolean overrideHostHeader = false;
        private int responseTimeoutMillis = DEFAULT_RESPONSE_TIMEOUT_MILLIS;
        private int maxHeaderSize = USE_DEFAULT_MAX_HEADER_SIZE;
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
            this.maxHeaderSize = backendService.maxHeaderSize;
            this.tlsSettings = backendService.tlsSettings().orElse(null);
            this.overrideHostHeader = backendService.overrideHostHeader;
        }

        /**
         * Adds an ID.
         *
         * @param id an ID
         * @return this builder
         */
        public Builder id(Id id) {
            this.id = requireNonNull(id);
            return this;
        }

        /**
         * Sets an ID.
         *
         * @param id an ID
         * @return this builder
         */
        public Builder id(String id) {
            return id(Id.id(id));
        }

        /**
         * Sets a path.
         *
         * @param path a path
         * @return this builder
         */
        public Builder path(String path) {
            this.path = checkValidPath(requireNonNull(path));
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
        public Builder responseTimeoutMillis(int timeout) {
            this.responseTimeoutMillis = timeout;
            return this;
        }

        /**
         * Sets the response max header size in bytes.
         * 0 means use the default.
         *
         * @param maxHeaderSize
         * @return
         */
        public Builder maxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }

        /**
         * Sets hosts.
         *
         * @param origins origins
         * @return this builder
         */
        public Builder origins(Set<Origin> origins) {
            this.origins = requireNonNull(origins);
            return this;
        }

        /**
         * Sets the https settings.
         * For Jackson JSON serialiser that de-serialises from Option&lt;TlsSettings&gt;.
         */
        Builder https(Optional<TlsSettings> tlsSettings) {
            this.tlsSettings = tlsSettings.orElse(null);
            return this;
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
        public Builder httpsOld(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        /**
         * Sets the https settings.
         * For programmatic use
         */
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
            return origins(Set.of(origins));
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
         * Sets whether incoming host header value should be replaced with origin host
         */
        public Builder overrideHostHeader(boolean overrideHostHeader) {
            this.overrideHostHeader = overrideHostHeader;
            return this;
        }

        /**
         * Sets rewrites to be performed on URLs.
         *
         * @param rewriteConfigs rewrite configuration
         * @return this builder
         */
        public Builder rewrites(List<RewriteConfig> rewriteConfigs) {
            this.rewrites = List.copyOf(rewriteConfigs);
            return this;
        }

        /**
         * Sets connection pool configuration.
         *
         * @param connectionPoolSettings connection pool configuration
         * @return this builder
         */
        public Builder connectionPoolConfig(ConnectionPoolSettings connectionPoolSettings) {
            this.connectionPoolSettings = requireNonNull(connectionPoolSettings);
            return this;
        }

        /**
         * Sets sticky-session configuration.
         *
         * @param stickySessionConfig sticky-session configuration.
         * @return this builder
         */
        public Builder stickySessionConfig(StickySessionConfig stickySessionConfig) {
            this.stickySessionConfig = requireNonNull(stickySessionConfig);
            return this;
        }

        /**
         * Sets health-check configuration.
         *
         * @param healthCheckConfig health-check configuration
         * @return this builder
         */
        public Builder healthCheckConfig(HealthCheckConfig healthCheckConfig) {
            this.healthCheckConfig = healthCheckConfig;
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
