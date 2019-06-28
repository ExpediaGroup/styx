/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.google.common.eventbus.EventBus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.proxy.HttpErrorStatusCauseLogger;
import com.hotels.styx.proxy.HttpErrorStatusMetrics;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.ServerEnvironment;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Environment: metrics, health check, build info, event bus.
 */
public final class Environment implements com.hotels.styx.api.Environment {
    private final Version version;
    private final EventBus eventBus;
    private final ConfigStore configStore;
    private final StyxConfig configuration;
    private final HttpErrorStatusListener httpErrorStatusListener;
    private final ServerEnvironment serverEnvironment;
    private final StartupConfig startupConfig;

    private Environment(Builder builder) {
        this.eventBus = firstNonNull(builder.eventBus, () -> new EventBus("Styx"));
        this.configStore = new ConfigStore();

        this.configuration = requireNonNull(builder.configuration);
        this.startupConfig = builder.startupConfig;
        this.version = firstNonNull(builder.version, Version::newVersion);
        this.serverEnvironment = new ServerEnvironment(firstNonNull(builder.metricRegistry, CodaHaleMetricRegistry::new));

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
    public StyxConfig configuration() {
        return this.configuration;
    }

    public StartupConfig startupConfig() {
        return startupConfig;
    }

    @Override
    public MetricRegistry metricRegistry() {
        return serverEnvironment.metricRegistry();
    }

    /**
     * @deprecated Use {@link #configuration()}
     *
     * @return configuration
     */
    @Deprecated
    public StyxConfig styxConfig() {
        return configuration();
    }

    public ServerEnvironment serverEnvironment() {
        return serverEnvironment;
    }


    /**
     * Builder for {@link com.hotels.styx.Environment}.
     */
    public static class Builder {
        private MetricRegistry metricRegistry;
        private Version version;
        private EventBus eventBus;
        private StyxConfig configuration = StyxConfig.defaultConfig();
        private StartupConfig startupConfig;

        public Builder configuration(StyxConfig configuration) {
            this.configuration = requireNonNull(configuration);
            return this;
        }

        public Builder metricRegistry(MetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;
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

        public Builder startupConfig(StartupConfig startupConfig) {
            this.startupConfig = startupConfig;
            return this;
        }

        public Environment build() {
            return new Environment(this);
        }
    }
}
