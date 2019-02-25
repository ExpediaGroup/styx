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
package com.hotels.styx.startup.extensions;

import com.hotels.styx.Environment;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginFactoriesLoader;
import com.hotels.styx.proxy.plugin.PluginsMetadata;
import org.slf4j.Logger;

import java.util.List;

import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.startup.extensions.FailureHandling.PLUGIN_FACTORY_LOADING_FAILURE_HANDLING_STRATEGY;
import static com.hotels.styx.startup.extensions.FailureHandling.PLUGIN_STARTUP_FAILURE_HANDLING_STRATEGY;
import static java.util.Collections.emptyList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Utility to start-up plugins with Styx.
 */
public final class PluginLoadingForStartup {
    private static final String DEFAULT_PLUGINS_METRICS_SCOPE = "styx.plugins";

    private static final Logger LOGGER = getLogger(PluginLoadingForStartup.class);

    private PluginLoadingForStartup() {
    }

    /**
     * Load plugins.
     *
     * @param environment environment
     * @param factories   if set, overrides config, otherwise plugins will be loaded from config
     * @return plugins
     */
    public static List<NamedPlugin> loadPlugins(Environment environment, List<ConfiguredPluginFactory> factories) {
        return loadPluginsFromFactories(environment, factories);
    }

    public static List<NamedPlugin> loadPlugins(Environment environment) {
        List<ConfiguredPluginFactory> activePlugins = loadFactoriesFromConfig(environment);

        return loadPluginsFromFactories(environment, activePlugins);
    }

    private static List<ConfiguredPluginFactory> loadFactoriesFromConfig(Environment environment) {
        PluginFactoriesLoader loader = new PluginFactoriesLoader(PLUGIN_FACTORY_LOADING_FAILURE_HANDLING_STRATEGY);

        return environment.configuration().get("plugins", PluginsMetadata.class)
                .map(loader::load)
                .orElse(emptyList());
    }

    private static List<NamedPlugin> loadPluginsFromFactories(Environment environment, List<ConfiguredPluginFactory> factories) {
        return PLUGIN_STARTUP_FAILURE_HANDLING_STRATEGY.process(factories, factory -> loadPlugin(environment, factory));
    }

    private static NamedPlugin loadPlugin(Environment environment, ConfiguredPluginFactory factory) {
        LOGGER.info("Instantiating Plugin, pluginName={}...", factory.name());

        PluginFactory.Environment pluginEnvironment = new PluginFactory.Environment() {
            @Override
            public <T> T pluginConfig(Class<T> clazz) {
                return factory.pluginConfig(clazz);
            }

            @Override
            public Configuration configuration() {
                return environment.configuration();
            }

            @Override
            public MetricRegistry metricRegistry() {
                return environment.metricRegistry().scope(DEFAULT_PLUGINS_METRICS_SCOPE + "." + factory.name());
            }
        };

        Plugin plugin = factory.pluginFactory().create(pluginEnvironment);

        LOGGER.info("Instantiated Plugin, pluginName={}", factory.name());

        return namedPlugin(factory.name(), plugin);
    }
}
