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
import com.hotels.styx.common.Pair;
import com.hotels.styx.proxy.plugin.FileSystemPluginFactoryLoader;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginStartupException;
import com.hotels.styx.proxy.plugin.PluginsMetadata;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hotels.styx.common.MapStream.stream;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginPipelineStatus.AT_LEAST_ONE_PLUGIN_FAILED;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.CONSTRUCTED;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.CONSTRUCTING;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.FAILED_WHILE_CONSTRUCTING;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.FAILED_WHILE_LOADING_CLASSES;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.LOADED_CLASSES;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.LOADING_CLASSES;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
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
        List<Pair<String, SpiExtension>> configList = environment.configuration().get("plugins", PluginsMetadata.class)
                .map(PluginsMetadata::activePlugins)
                .orElse(emptyList());

        PluginStatusNotifications notifications = new PluginStatusNotifications(environment.configStore());

        List<ConfiguredPluginFactory> factories = new ArrayList<>();
        Map<Pair<String, SpiExtension>, Exception> failures = new HashMap<>();

        for (Pair<String, SpiExtension> config : configList) {
            try {
                notifications.notifyPluginStatus(config.key(), LOADING_CLASSES);
                factories.add(loadPluginFactory(config));
                notifications.notifyPluginStatus(config.key(), LOADED_CLASSES);
            } catch (Exception e) {
                notifications.notifyPluginStatus(config.key(), FAILED_WHILE_LOADING_CLASSES);
                environment.configStore().set("startup.plugins." + config.key(), "failed-while-loading");
                LOGGER.error(format("Could not load plugin: pluginName=%s; factoryClass=%s", config.key(), config.value().factory().factoryClass()), e);
                failures.put(config, e);
            }
        }

        if (!failures.isEmpty()) {
            notifications.notifyPluginPipelineStatus(AT_LEAST_ONE_PLUGIN_FAILED);
            throw new PluginStartupException(afterFailuresErrorMessage(failures, Pair::key));
        }

        return factories;
    }

    private static ConfiguredPluginFactory loadPluginFactory(Pair<String, SpiExtension> pair) {
        String pluginName = pair.key();
        SpiExtension spiExtension = pair.value();

        PluginFactory factory = new FileSystemPluginFactoryLoader().load(spiExtension);

        return new ConfiguredPluginFactory(pluginName, factory, spiExtension::config);
    }

    private static List<NamedPlugin> loadPluginsFromFactories(Environment environment, List<ConfiguredPluginFactory> factories) {
        PluginStatusNotifications notifications = new PluginStatusNotifications(environment.configStore());

        List<NamedPlugin> plugins = new ArrayList<>();
        Map<ConfiguredPluginFactory, Exception> failures = new HashMap<>();

        for (ConfiguredPluginFactory factory : factories) {
            try {
                notifications.notifyPluginStatus(factory.name(), CONSTRUCTING);
                plugins.add(loadPlugin(environment, factory));
                notifications.notifyPluginStatus(factory.name(), CONSTRUCTED);
            } catch (Exception e) {
                notifications.notifyPluginStatus(factory.name(), FAILED_WHILE_CONSTRUCTING);
                LOGGER.error(format("Could not load plugin: pluginName=%s; factoryClass=%s", factory.name(), factory.pluginFactory().getClass().getName()), e);
                failures.put(factory, e);
            }
        }

        if (!failures.isEmpty()) {
            notifications.notifyPluginPipelineStatus(AT_LEAST_ONE_PLUGIN_FAILED);
            throw new PluginStartupException(afterFailuresErrorMessage(failures, ConfiguredPluginFactory::name));
        }

        return plugins;
    }

    private static NamedPlugin loadPlugin(Environment environment, ConfiguredPluginFactory factory) {
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

        return namedPlugin(factory.name(), plugin);
    }

    private static <K> String afterFailuresErrorMessage(Map<K, Exception> failures, Function<K, String> getPluginName) {
        List<String> failedPlugins = mapKeys(failures, getPluginName);

        List<String> causes = mapEntries(failures, (key, err) -> {
            // please note, transforming the exception to a String (as is done here indirectly) will not include the stack trace
            return format("%s: %s", getPluginName.apply(key), err);
        });

        return format("%s plugin(s) could not be loaded: failedPlugins=%s; failureCauses=%s", failures.size(), failedPlugins, causes);
    }

    private static <R, K, V> List<R> mapKeys(Map<K, V> map, Function<K, R> function) {
        return stream(map)
                .mapToObject((k, v) -> function.apply(k))
                .collect(toList());
    }

    private static <R, K, V> List<R> mapEntries(Map<K, V> map, BiFunction<K, V, R> function) {
        return stream(map)
                .mapToObject(function)
                .collect(toList());
    }
}
