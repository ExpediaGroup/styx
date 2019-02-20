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
import com.google.common.collect.ImmutableList;
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
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginSuppliers;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static com.hotels.styx.Version.readVersionFrom;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.proxy.plugin.PluginSuppliers.DEFAULT_PLUGINS_METRICS_SCOPE;
import static com.hotels.styx.startup.ServicesLoader.SERVICES_FROM_CONFIG;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.DO_NOT_MODIFY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration required to set-up the core Styx services, such as the proxy and admin servers.
 */
public class StyxServerComponents {
    private static final Logger LOGGER = getLogger(StyxServerComponents.class);

    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;

    // TODO this method is now big and ugly. split it up.
    private StyxServerComponents(Builder builder) {
        StyxConfig styxConfig = requireNonNull(builder.styxConfig);

        this.environment = newEnvironment(styxConfig, builder.metricRegistry);
        builder.loggingSetUp.setUp(environment);

        List<ConfiguredPluginFactory> cpfs = builder.configuredPluginFactories;

        if (cpfs == null) {
            Iterable<ConfiguredPluginFactory> iterable = new PluginSuppliers(environment).fromConfigurations();
            cpfs = ImmutableList.copyOf(iterable);
        }

        this.plugins = cpfs.stream().map(cpf -> {
            LOGGER.info("Instantiating Plugin, pluginName={}...", cpf.name());

            PluginFactory.Environment pluginEnvironment = new PluginFactory.Environment() {
                @Override
                public <T> T pluginConfig(Class<T> clazz) {
                    return cpf.pluginConfig(clazz);
                }

                @Override
                public Configuration configuration() {
                    return environment.configuration();
                }

                @Override
                public MetricRegistry metricRegistry() {
                    return environment.metricRegistry().scope(DEFAULT_PLUGINS_METRICS_SCOPE);
                }
            };

            Plugin plugin = cpf.pluginFactory().create(pluginEnvironment);

            // TODO refactor so we don't have casting (code smell)
            return plugin instanceof NamedPlugin ? (NamedPlugin) plugin : namedPlugin(cpf.name(), plugin);
        }).collect(toList());


        this.plugins.forEach(plugin -> {
            LOGGER.info("Instantiated Plugin, pluginName={}", plugin.name());

            environment.configStore().set("plugins." + plugin.name(), plugin);
        });

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

    private static Environment newEnvironment(StyxConfig styxConfig, MetricRegistry metricRegistry) {
        return new Environment.Builder()
                .configuration(styxConfig)
                .metricsRegistry(metricRegistry)
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
        public Builder plugins(Iterable<NamedPlugin> plugins) {
            List<ConfiguredPluginFactory> cpfs = StreamSupport.stream(plugins.spliterator(), false)
                    .map(plugin -> new ConfiguredPluginFactory(plugin.name(), any -> plugin, null))
                    .collect(toList());

            return pluginFactories(cpfs);
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
     * TODO this can be moved if we desire. If not, replace this comment with proper documentation.
     */
    public static class ConfiguredPluginFactory {
        private final String name;
        private final PluginFactory pluginFactory;
        private final Function<Class<?>, Object> configProvider;

        public ConfiguredPluginFactory(String name, PluginFactory pluginFactory, Object pluginConfig) {
            this.name = requireNonNull(name);
            this.pluginFactory = requireNonNull(pluginFactory);
            this.configProvider = type -> type.cast(pluginConfig);
        }

        public ConfiguredPluginFactory(String name, PluginFactory pluginFactory, Function<Class<?>, Object> configProvider) {
            this.name = requireNonNull(name);
            this.pluginFactory = requireNonNull(pluginFactory);
            this.configProvider = configProvider == null ? any -> null : configProvider;
        }

        public String name() {
            return name;
        }

        public PluginFactory pluginFactory() {
            return pluginFactory;
        }

        public <T> T pluginConfig(Class<T> clazz) {
            return (T) configProvider.apply(clazz);
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
