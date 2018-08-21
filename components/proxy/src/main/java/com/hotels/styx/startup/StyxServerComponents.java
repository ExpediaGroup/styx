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
package com.hotels.styx.startup;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.AsyncEventBus;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.Version;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.proxy.plugin.NamedPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hotels.styx.Version.readVersionFrom;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.startup.PluginsLoader.PLUGINS_FROM_CONFIG;
import static com.hotels.styx.startup.ServicesLoader.SERVICES_FROM_CONFIG;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.DO_NOT_MODIFY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Configuration required to set-up the core Styx services, such as the proxy and admin servers.
 */
public class StyxServerComponents {
    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;

    private StyxServerComponents(Builder builder) {
        StyxConfig styxConfig = requireNonNull(builder.styxConfig);

        this.environment = newEnvironment(styxConfig);
        builder.loggingSetUp.setUp(environment);

        this.plugins = builder.pluginsLoader.load(environment);

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

    public List<NamedPlugin> plugins() {
        return plugins;
    }

    private static Environment newEnvironment(StyxConfig styxConfig) {
        return new Environment.Builder()
                .configuration(styxConfig)
                .metricsRegistry(new CodaHaleMetricRegistry())
                .healthChecksRegistry(new HealthCheckRegistry())
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
        private PluginsLoader pluginsLoader = PLUGINS_FROM_CONFIG;
        private ServicesLoader servicesLoader = SERVICES_FROM_CONFIG;

        private final Map<String, StyxService> additionalServices = new HashMap<>();

        public Builder styxConfig(StyxConfig styxConfig) {
            this.styxConfig = requireNonNull(styxConfig);
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
        public Builder plugins(Iterable<NamedPlugin> plugins) {
            requireNonNull(plugins);
            List<NamedPlugin> list = ImmutableList.copyOf(plugins);
            return plugins(env -> list);
        }

        public Builder plugins(PluginsLoader pluginsLoader) {
            this.pluginsLoader = requireNonNull(pluginsLoader);
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
