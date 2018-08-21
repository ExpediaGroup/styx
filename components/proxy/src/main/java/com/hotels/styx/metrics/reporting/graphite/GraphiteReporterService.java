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
package com.hotels.styx.metrics.reporting.graphite;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricFilter.ALL;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builds graphite reporter from configuration and wraps it in service interface.
 */
public final class GraphiteReporterService extends AbstractStyxService {
    private static final Logger LOGGER = getLogger(GraphiteReporterService.class);

    private final GraphiteReporter reporter;
    private final long reportingIntervalMillis;

    private GraphiteReporterService(Builder builder) {
        super(requireNonNull(builder.serviceName));

        MetricRegistry registry = requireNonNull(builder.registry);
        GraphiteSender graphiteSender = requireNonNull(builder.graphiteSender);
        String prefix = requireNonNull(builder.prefix);

        this.reportingIntervalMillis = builder.reportingIntervalMillis;
        this.reporter = GraphiteReporter.forRegistry(registry)
                .prefixedWith(prefix)
                .convertRatesTo(SECONDS)
                .convertDurationsTo(MILLISECONDS)
                .filter(ALL)
                .build(graphiteSender);
    }

    @Override
    protected CompletableFuture<Void> startService() {
        return CompletableFuture.runAsync(() -> {
            this.reporter.start(reportingIntervalMillis, MILLISECONDS);
            LOGGER.info("Graphite service started, service name=\"{}\"", serviceName());
        });
    }

    @Override
    protected CompletableFuture<Void> stopService() {
        return CompletableFuture.runAsync(() -> {
            this.reporter.stop();
            LOGGER.info("Graphite service stopped, service name=\"{}\"", serviceName());
        });
    }

    @VisibleForTesting
    void report() {
        this.reporter.report();
    }

    /**
     * Builder for reporter service.
     */
    public static final class Builder {
        private String serviceName;
        private String prefix;
        private long reportingIntervalMillis;
        private MetricRegistry registry;
        private GraphiteSender graphiteSender;

        public Builder metricRegistry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder serviceName(String name) {
            this.serviceName = name;
            return this;
        }

        public Builder graphiteSender(GraphiteSender graphiteSender) {
            this.graphiteSender = graphiteSender;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder reportingInterval(long reportingInterval, TimeUnit timeUnit) {
            this.reportingIntervalMillis = timeUnit.toMillis(reportingInterval);
            return this;
        }

        public GraphiteReporterService build() {
            return new GraphiteReporterService(this);
        }
    }
}
