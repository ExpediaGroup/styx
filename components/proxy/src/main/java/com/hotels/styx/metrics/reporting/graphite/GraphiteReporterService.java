/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.metrics.reporting.graphite;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.service.spi.StyxService;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricFilter.ALL;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builds graphite reporter from configuration and wraps it in service interface.
 */
public final class GraphiteReporterService implements StyxService {
    private static final Logger LOGGER = getLogger(GraphiteReporterService.class);

    private final GraphiteReporter reporter;
    private final long reportingIntervalMillis;
    private final InetSocketAddress address;

    private GraphiteReporterService(Builder builder) {
        this.address = builder.inetSocketAddress;
        MetricRegistry registry = checkNotNull(builder.registry);
        GraphiteSender graphiteSender = Optional
                .ofNullable(builder.graphiteSender)
                .orElseGet(() -> new NonSanitizingGraphite(address));

        String prefix = checkNotNull(builder.prefix);

        this.reportingIntervalMillis = builder.reportingIntervalMillis;
        this.reporter = GraphiteReporter.forRegistry(registry)
                .prefixedWith(prefix)
                .convertRatesTo(SECONDS)
                .convertDurationsTo(MILLISECONDS)
                .filter(ALL)
                .build(graphiteSender);
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info("Graphite started on address=\"{}\"", address);
            this.reporter.start(reportingIntervalMillis, MILLISECONDS);
        });
    }

    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(this.reporter::stop);
    }

    @VisibleForTesting
    void report() {
        this.reporter.report();
    }

    // The sanitize method in Graphite/PickledGraphite adds a lot of object creation. We do not need it because our
    // metric names and values do not contain whitespace.
    private static final class NonSanitizingGraphite extends Graphite {
        private NonSanitizingGraphite(InetSocketAddress address) {
            super(address);
        }

        @Override
        protected String sanitize(String s) {
            return s;
        }
    }

    /**
     * Builder for reporter service.
     */
    public static final class Builder {
        private String prefix;
        private InetSocketAddress inetSocketAddress;
        private long reportingIntervalMillis;
        private MetricRegistry registry;
        private GraphiteSender graphiteSender;

        public Builder metricRegistry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        @VisibleForTesting
        Builder graphiteSender(GraphiteSender graphiteSender) {
            this.graphiteSender = graphiteSender;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder address(String host, int port) {
            this.inetSocketAddress = new InetSocketAddress(host, port);
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
