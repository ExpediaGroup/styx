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
package com.hotels.styx;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.proxy.HttpErrorStatusCauseLogger;
import com.hotels.styx.proxy.HttpErrorStatusMetrics;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.ServerEnvironment;

import java.util.function.Supplier;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;

/**
 * Environment: metrics, health check, build info, event bus.
 */
public final class Environment implements com.hotels.styx.api.Environment {
    private final Version version;
    private final EventBus eventBus;
    private final ConfigStore configStore;
    private final AggregatedConfiguration aggregatedConfiguration;
    private final HttpErrorStatusListener httpErrorStatusListener;
    private final ServerEnvironment serverEnvironment;

    private Environment(Builder builder) {
        this.eventBus = firstNonNull(builder.eventBus, () -> new EventBus("Styx"));
        this.configStore = new ConfigStore();

        this.aggregatedConfiguration = firstNonNull(builder.aggregatedConfiguration, () -> new AggregatedConfiguration(new StyxConfig()));
        this.version = firstNonNull(builder.version, Version::newVersion);
        this.serverEnvironment = new ServerEnvironment(
                firstNonNull(builder.metricRegistry, CodaHaleMetricRegistry::new),
                firstNonNull(builder.healthCheckRegistry, HealthCheckRegistry::new));

        this.httpErrorStatusListener = HttpErrorStatusListener.compose(new HttpErrorStatusCauseLogger(), new HttpErrorStatusMetrics(serverEnvironment.metricRegistry()));
    }

    // prevent unnecessary construction of defaults
    private static <T> T firstNonNull(T one, Supplier<T> two) {
        return one != null ? one : two.get();
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public ConfigStore configStore() {
        return configStore;
    }

    public Version buildInfo() {
        return version;
    }

    public HttpErrorStatusListener errorListener() {
        return this.httpErrorStatusListener;
    }

    @Override
    public AggregatedConfiguration configuration() {
        return this.aggregatedConfiguration;
    }

    @Override
    public CodaHaleMetricRegistry metricRegistry() {
        return serverEnvironment.metricRegistry();
    }

    @Override
    public HealthCheckRegistry healthCheckRegistry() {
        return serverEnvironment.healthCheckRegistry();
    }

    public StyxConfig styxConfig() {
        return aggregatedConfiguration.styxConfig();
    }

    public ServerEnvironment serverEnvironment() {
        return serverEnvironment;
    }


    /**
     * Builder for {@link com.hotels.styx.Environment}.
     */
    public static class Builder {
        private AggregatedConfiguration aggregatedConfiguration;
        private CodaHaleMetricRegistry metricRegistry;
        private HealthCheckRegistry healthCheckRegistry;
        private Version version;
        private EventBus eventBus;

        /**
         * Sets aggregated configuration.
         *
         * @deprecated see {@link AggregatedConfiguration}
         */
        @Deprecated
        public Builder aggregatedConfiguration(AggregatedConfiguration aggregatedConfiguration) {
            this.aggregatedConfiguration = aggregatedConfiguration;
            return this;
        }

        public Builder configuration(StyxConfig configuration) {
            this.aggregatedConfiguration = new AggregatedConfiguration(configuration, EMPTY_CONFIGURATION);
            return this;
        }

        public Builder metricsRegistry(CodaHaleMetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;
            return this;
        }

        public Builder healthChecksRegistry(HealthCheckRegistry healthCheckRegistry) {
            this.healthCheckRegistry = healthCheckRegistry;
            return this;
        }

        public Builder buildInfo(Version version) {
            this.version = version;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Environment build() {
            return new Environment(this);
        }
    }
}
