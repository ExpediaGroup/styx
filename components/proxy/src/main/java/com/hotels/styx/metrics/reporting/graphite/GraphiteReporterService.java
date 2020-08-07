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
package com.hotels.styx.metrics.reporting.graphite;

import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteDimensionalNameMapper;
import io.micrometer.graphite.GraphiteHierarchicalNameMapper;
import io.micrometer.graphite.GraphiteMeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builds graphite reporter from configuration and wraps it in service interface.
 */
public final class GraphiteReporterService extends AbstractStyxService {
    private static final Logger LOGGER = getLogger(GraphiteReporterService.class);
    private final MeterRegistry meterRegistry;
    private final MicrometerGraphiteConfig graphiteConfig;
    private GraphiteMeterRegistry graphiteMeterRegistry;

    private GraphiteReporterService(Builder builder) {
        super(builder.serviceName);

        meterRegistry = requireNonNull(builder.meterRegistry);
        graphiteConfig = new MicrometerGraphiteConfig(builder);
    }

    @Override
    protected CompletableFuture<Void> startService() {
        return CompletableFuture.runAsync(() -> {
            String metricPrefix = Optional.ofNullable(graphiteConfig.metricNamePrefix())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s + ".")
                    .orElse("");
            HierarchicalNameMapper nameMapper = graphiteConfig.graphiteTagsEnabled()
                    ? new GraphiteDimensionalNameMapper()
                    : new GraphiteHierarchicalNameMapper();

            graphiteMeterRegistry = new GraphiteMeterRegistry(
                    graphiteConfig,
                    Clock.SYSTEM,
                    (id, convention) -> metricPrefix + nameMapper.toHierarchicalName(id, convention));
            ((CompositeMeterRegistry) meterRegistry).add(graphiteMeterRegistry);
            LOGGER.info("Graphite service started, service name=\"{}\"", serviceName());
        });
    }

    @Override
    protected CompletableFuture<Void> stopService() {
        return CompletableFuture.runAsync(() -> {
            graphiteMeterRegistry.stop();
            ((CompositeMeterRegistry) meterRegistry).remove(graphiteMeterRegistry);
            LOGGER.info("Graphite service stopped, service name=\"{}\"", serviceName());
        });
    }

    /**
     * Builder for reporter service.
     */
    public static final class Builder {
        private MeterRegistry meterRegistry;
        private String serviceName;
        private String host;
        private int port;
        private String prefix;
        private long reportingIntervalMillis;
        private boolean enabled;
        private boolean tagsEnabled;

        public Builder meterRegistry(@NotNull MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }

        public Builder serviceName(@NotNull String name) {
            this.serviceName = name;
            return this;
        }

        public Builder host(@NotNull String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder reportingIntervalMillis(long reportingIntervalMillis) {
            this.reportingIntervalMillis = reportingIntervalMillis;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder tagsEnabled(boolean tagsEnabled) {
            this.tagsEnabled = tagsEnabled;
            return this;
        }

        public GraphiteReporterService build() {
            return new GraphiteReporterService(this);
        }
    }

    private static final class MicrometerGraphiteConfig implements GraphiteConfig {
        private final Builder builder;

        public MicrometerGraphiteConfig(Builder builder) {
            this.builder = builder;
        }

        @Override
        public String get(@NotNull String s) {
            return null;
        }

        @Override
        public boolean graphiteTagsEnabled() {
            return builder.tagsEnabled;
        }

        @NotNull
        @Override
        public String host() {
            return builder.host;
        }

        @Override
        public int port() {
            return builder.port;
        }

        @Override
        public boolean enabled() {
            return builder.enabled;
        }

        @NotNull
        @Override
        public Duration step() {
            return Duration.ofMillis(builder.reportingIntervalMillis);
        }

        public String metricNamePrefix() {
            return builder.prefix;
        }

    }
}
