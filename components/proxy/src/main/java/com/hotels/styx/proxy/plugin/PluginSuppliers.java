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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A helper class for creating plugin supplier objects.
 */
public class PluginSuppliers {
    private static final String DEFAULT_PLUGINS_METRICS_SCOPE = "styx.plugins";
    private static final Logger LOG = getLogger(PluginSuppliers.class);

    private final Configuration configuration;
    private final PluginFactoryLoader pluginFactoryLoader;
    private final Environment environment;

    private final FailureHandlingStrategy<Pair<String, SpiExtension>, NamedPlugin> failureHandlingStrategy =
            new FailureHandlingStrategy.Builder<Pair<String, SpiExtension>, NamedPlugin>()
                    .doImmediatelyOnEachFailure((plugin, err) ->
                            LOG.error(perFailureErrorMessage(plugin), err))
                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(failures));
                    }).build();

    public PluginSuppliers(Environment environment) {
        this(environment, new FileSystemPluginFactoryLoader());
    }

    PluginSuppliers(Environment environment, PluginFactoryLoader pluginFactoryLoader) {
        this.configuration = environment.configuration();
        this.pluginFactoryLoader = requireNonNull(pluginFactoryLoader);
        this.environment = requireNonNull(environment);
    }

    private Optional<PluginsMetadata> readPluginsConfig() {
        return configuration.get("plugins", PluginsMetadata.class);
    }

    public Iterable<NamedPlugin> fromConfigurations() {
        return readPluginsConfig()
                .map(this::activePlugins)
                .orElse(emptyList());
    }

    private Iterable<NamedPlugin> activePlugins(PluginsMetadata pluginsMetadata) {
        return failureHandlingStrategy.process(pluginsMetadata.activePlugins(), this::loadPlugin);
    }

    private NamedPlugin loadPlugin(Pair<String, SpiExtension> pair) {
        String pluginName = pair.key();
        SpiExtension spiExtension = pair.value();

        PluginFactory factory = pluginFactoryLoader.load(spiExtension);
        Plugin plugin = factory.create(new PluginEnvironment(pluginName, environment, spiExtension, DEFAULT_PLUGINS_METRICS_SCOPE));
        return namedPlugin(pluginName, plugin);
    }

    private static String perFailureErrorMessage(Pair<String, SpiExtension> plugin) {
        return format("Could not load plugin: pluginName=%s; factoryClass=%s", plugin.key(), plugin.value().factory().factoryClass());
    }

    private static String afterFailuresErrorMessage(Map<Pair<String, SpiExtension>, Exception> failures) {
        List<String> failedPlugins = failures.keySet().stream()
                .map(Pair::key)
                .collect(toList());

        List<String> causes = failures.entrySet().stream().map(entry -> {
            String pluginName = entry.getKey().key();
            Throwable throwable = entry.getValue();

            // please note, transforming the exception to a String (as is done here indirectly) will not include the stack trace
            return format("%s: %s", pluginName, throwable);

        }).collect(toList());

        return format("%s plugin%s could not be loaded: failedPlugins=%s; failureCauses=%s",
                failures.size(),
                failures.size() == 1 ? "" : "s", // only pluralise plurals
                failedPlugins,
                causes
        );
    }
}
