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
package com.hotels.styx.api.extension.service;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Configuration for health-checking.
 */
public final class HealthCheckConfig {
    public static final int DEFAULT_HEALTHY_THRESHOLD_VALUE = 2;
    public static final int DEFAULT_UNHEALTHY_THRESHOLD_VALUE = 2;
    public static final Long DEFAULT_HEALTH_CHECK_INTERVAL = 5000L;
    public static final Long DEFAULT_TIMEOUT_VALUE = 2000L;

    private final Optional<String> uri;
    private final long intervalMillis;
    private final long timeoutMillis;
    private final int healthyThreshold;
    private final int unhealthyThreshold;

    private HealthCheckConfig() {
        this(newHealthCheckConfigBuilder());
    }

    private HealthCheckConfig(Builder builder) {
        this(builder.uri,
                builder.intervalMillis,
                builder.timeoutMillis,
                builder.healthyThreshold,
                builder.unhealthyThreshold);
    }

    private HealthCheckConfig(Optional<String> uri, Optional<Long> intervalMillis, Optional<Long> timeoutMillis,
                              Optional<Integer> healthyThreshold, Optional<Integer> unhealthyThreshold) {
        this.uri = uri.map(this::checkValidUri);
        this.intervalMillis = zeroToAbsent(intervalMillis).orElse(DEFAULT_HEALTH_CHECK_INTERVAL);
        this.timeoutMillis = zeroToAbsent(timeoutMillis).orElse(DEFAULT_TIMEOUT_VALUE);
        this.healthyThreshold = healthyThreshold.orElse(DEFAULT_HEALTHY_THRESHOLD_VALUE);
        this.unhealthyThreshold = unhealthyThreshold.orElse(DEFAULT_UNHEALTHY_THRESHOLD_VALUE);

        checkArgument(this.intervalMillis >= 1, format("intervalMillis [%s] cannot be < 1 ms", intervalMillis));
        checkArgument(this.timeoutMillis >= 1, format("timeoutMillis [%s] cannot be < 1 ms", intervalMillis));
        checkArgument(this.healthyThreshold >= 1, format("healthyThreshold [%s] cannot be < 1", healthyThreshold));
        checkArgument(this.unhealthyThreshold >= 1, format("unhealthyThreshold [%s] cannot be < 1", unhealthyThreshold));
    }

    private String checkValidUri(String uri) {
        try {
            URI.create(uri);
            return uri;
        } catch (Throwable cause) {
            String message = format("Invalid health check URI. URI='%s'", uri);
            throw new IllegalArgumentException(message, cause);
        }
    }

    /**
     * Configuration that has health-checking disabled.
     *
     * @return new configuration
     */
    public static HealthCheckConfig noHealthCheck() {
        return new HealthCheckConfig();
    }

    private static Optional<Long> zeroToAbsent(Optional<Long> optional) {
        if (!optional.isPresent() || optional.get() == 0) {
            return Optional.empty();
        }

        return optional;
    }

    /**
     * Health check URI.
     *
     * @return health check URI
     */
    public Optional<String> uri() {
        return uri;
    }

    String getUri() {
        return uri.orElse(null);
    }

    /**
     * Health check interval in milliseconds.
     *
     * @return health check interval
     */
    public long intervalMillis() {
        return intervalMillis;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public int healthyThreshold() {
        return healthyThreshold;
    }

    public int unhealthyThreshold() {
        return unhealthyThreshold;
    }

    public boolean isEnabled() {
        return uri.isPresent();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.uri, this.intervalMillis, this.timeoutMillis, this.healthyThreshold, this.unhealthyThreshold);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HealthCheckConfig other = (HealthCheckConfig) obj;
        return Objects.equals(this.uri, other.uri)
                && Objects.equals(this.intervalMillis, other.intervalMillis)
                && Objects.equals(this.timeoutMillis, other.timeoutMillis)
                && Objects.equals(this.healthyThreshold, other.healthyThreshold)
                && Objects.equals(this.unhealthyThreshold, other.unhealthyThreshold);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("uri", this.uri)
                .add("intervalMillis", this.intervalMillis)
                .add("timeoutMillis", this.timeoutMillis)
                .add("healthyThreshold", this.healthyThreshold)
                .add("unhealthyThreshold", this.unhealthyThreshold)
                .toString();
    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder newHealthCheckConfigBuilder() {
        return new Builder();
    }

    /**
     * Create a new builder that inherits properties from an existing configuration.
     *
     * @param healthCheckConfig existing configuration
     * @return new builder with inherited properties
     */
    public static Builder newHealthCheckConfigBuilder(HealthCheckConfig healthCheckConfig) {
        return new Builder(healthCheckConfig);
    }

    /**
     * A builder of {@link HealthCheckConfig}s.
     */
    public static final class Builder {
        private Optional<String> uri = Optional.empty();
        private Optional<Long> intervalMillis = Optional.empty();
        private Optional<Long> timeoutMillis = Optional.empty();
        private Optional<Integer> healthyThreshold = Optional.empty();
        private Optional<Integer> unhealthyThreshold = Optional.empty();

        private Builder() {
        }

        private Builder(HealthCheckConfig healthCheckConfig) {
            this.uri = healthCheckConfig.uri;
            this.intervalMillis = Optional.of(healthCheckConfig.intervalMillis);
            this.healthyThreshold = Optional.of(healthCheckConfig.healthyThreshold);
            this.unhealthyThreshold = Optional.of(healthCheckConfig.unhealthyThreshold);
        }

        /**
         * Sets the URI that health-checks should attempt to connect to. If null, no health-checks will be performed.
         *
         * @param uri a URI
         * @return this builder
         */
        public Builder uri(String uri) {
            return uri(Optional.ofNullable(uri));
        }

        /**
         * Sets the URI that health-checks should attempt to connect to. If absent, no health-checks will be performed.
         *
         * @param uri a URI
         * @return this builder
         */
        public Builder uri(Optional<String> uri) {
            this.uri = requireNonNull(uri);
            return this;
        }

        /**
         * Sets the interval between health-checks in milliseconds.
         *
         * @param interval interval in milliseconds
         * @return this builder
         */
        public Builder interval(long interval) {
            return interval(interval, TimeUnit.MILLISECONDS);
        }

        /**
         * Sets the interval between health-checks in a specified unit.
         *
         * @param interval interval in the specified unit
         * @param timeUnit time unit of interval
         * @return this builder
         */
        public Builder interval(long interval, TimeUnit timeUnit) {
            this.intervalMillis = Optional.of(timeUnit.toMillis(interval));
            return this;
        }

        /**
         * Sets the socket timeout for health-checks in milliseconds.
         *
         * @param timeout timeout in milliseconds
         * @return this builder
         */
        public Builder timeout(long timeout) {
            return timeout(timeout, TimeUnit.MILLISECONDS);
        }

        /**
         * Sets the socket timeout for health-checks in a specified unit.
         *
         * @param timeout timeout in the specified unit
         * @param timeUnit time unit of timeout
         * @return this builder
         */
        public Builder timeout(long timeout, TimeUnit timeUnit) {
            this.timeoutMillis = Optional.of(timeUnit.toMillis(timeout));
            return this;
        }

        /**
         * Sets the number of healthy results that must be received before an origin will be considered healthy.
         *
         * @param healthyThreshold the number of healthy results
         * @return this builder
         */
        public Builder healthyThreshold(int healthyThreshold) {
            this.healthyThreshold = Optional.of(healthyThreshold);
            return this;
        }

        /**
         * Sets the number of unhealthy results that must be received before an origin will be considered unhealthy.
         *
         * @param unhealthyThreshold the number of unhealthy results
         * @return this builder
         */
        public Builder unhealthyThreshold(int unhealthyThreshold) {
            this.unhealthyThreshold = Optional.of(unhealthyThreshold);
            return this;
        }

        /**
         * Build a new config based on the properties set in this builder.
         *
         * @return a new config
         */
        public HealthCheckConfig build() {
            return new HealthCheckConfig(this);
        }
    }
}
