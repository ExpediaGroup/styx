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
package com.hotels.styx.startup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.AsyncEventBus;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.Version.readVersionFrom;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.startup.ServicesLoader.SERVICES_FROM_CONFIG;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.DO_NOT_MODIFY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toList;

/**
 * Configuration required to set-up the core Styx services, such as the proxy and admin servers.
 */
public class StyxServerComponents {
    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<ConfiguredPluginFactory> pluginFactories;

    private StyxServerComponents(Builder builder) {
        StyxConfig styxConfig = requireNonNull(builder.styxConfig);

        this.environment = newEnvironment(styxConfig, builder.metricRegistry);
        builder.loggingSetUp.setUp(environment);

        this.pluginFactories = builder.configuredPluginFactories;

        this.services = mergeServices(
                builder.servicesLoader.load(environment),
                builder.additionalServices
        );
    }

    public Environment environment() {
        return environment;
    }

    public Map<String, StyxService> services() {
        return services;
    }

    public Optional<List<ConfiguredPluginFactory>> pluginFactories() {
        return Optional.ofNullable(pluginFactories);
    }

    private static Environment newEnvironment(StyxConfig styxConfig, MetricRegistry metricRegistry) {
        return new Environment.Builder()
                .configuration(styxConfig)
                .metricRegistry(metricRegistry)
                .buildInfo(readBuildInfo())
                .eventBus(new AsyncEventBus("styx", newSingleThreadExecutor()))
                .build();
    }

    private static Version readBuildInfo() {
        return readVersionFrom("/version.json");
    }

    private static void setUpLogging(String logConfigLocation) {
        initLogging(logConfigLocation, true);
    }

    private static Map<String, StyxService> mergeServices(Map<String, StyxService> configServices, Map<String, StyxService> additionalServices) {
        if (additionalServices == null) {
            return configServices;
        }

        return new ImmutableMap.Builder<String, StyxService>()
                .putAll(configServices)
                .putAll(additionalServices)
                .build();
    }

    /**
     * CoreConfig builder.
     */
    public static final class Builder {
        private StyxConfig styxConfig;
        private LoggingSetUp loggingSetUp = DO_NOT_MODIFY;
        private List<ConfiguredPluginFactory> configuredPluginFactories;
        private ServicesLoader servicesLoader = SERVICES_FROM_CONFIG;
        private MetricRegistry metricRegistry = new CodaHaleMetricRegistry();

        private final Map<String, StyxService> additionalServices = new HashMap<>();

        public Builder styxConfig(StyxConfig styxConfig) {
            this.styxConfig = requireNonNull(styxConfig);
            return this;
        }

        public Builder metricsRegistry(MetricRegistry metricRegistry) {
            this.metricRegistry = requireNonNull(metricRegistry);
            return this;
        }

        public Builder configuration(Configuration configuration) {
            return styxConfig(new StyxConfig(configuration));
        }

        public Builder loggingSetUp(LoggingSetUp loggingSetUp) {
            this.loggingSetUp = requireNonNull(loggingSetUp);
            return this;
        }

        @VisibleForTesting
        public Builder loggingSetUp(String logConfigLocation) {
            this.loggingSetUp = LoggingSetUp.from(logConfigLocation);
            return this;
        }

        @VisibleForTesting
        public Builder plugins(Map<String, Plugin> plugins) {
            return pluginFactories(stubFactories(plugins));
        }

        private static List<ConfiguredPluginFactory> stubFactories(Map<String, Plugin> plugins) {
            return plugins.entrySet().stream().map(entry -> {
                String name = entry.getKey();
                Plugin plugin = entry.getValue();

                return new ConfiguredPluginFactory(name, any -> plugin);
            }).collect(toList());
        }

        public Builder pluginFactories(List<ConfiguredPluginFactory> configuredPluginFactories) {
            this.configuredPluginFactories = requireNonNull(configuredPluginFactories);
            return this;
        }

        @VisibleForTesting
        Builder services(ServicesLoader servicesLoader) {
            this.servicesLoader = requireNonNull(servicesLoader);
            return this;
        }

        @VisibleForTesting
        public Builder additionalServices(Map<String, StyxService> services) {
            this.additionalServices.putAll(services);
            return this;
        }

        public StyxServerComponents build() {
            return new StyxServerComponents(this);
        }
    }

    /**
     * Set-up the logging.
     */
    public interface LoggingSetUp {
        LoggingSetUp DO_NOT_MODIFY = environment -> {
        };
        LoggingSetUp FROM_CONFIG = environment -> setUpLogging(environment.configuration().logConfigLocation());

        void setUp(Environment environment);

        static LoggingSetUp from(String logConfigLocation) {
            return environment -> setUpLogging(logConfigLocation);
        }
    }
}
