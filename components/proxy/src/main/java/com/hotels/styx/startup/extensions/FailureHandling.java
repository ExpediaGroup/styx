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

import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginStartupException;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Strategies for handling failure during start-up.
 */
final class FailureHandling {
    private static final Logger LOGGER = getLogger(FailureHandling.class);

    static final FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> PLUGIN_FACTORY_LOADING_FAILURE_HANDLING_STRATEGY =
            new FailureHandlingStrategy.Builder<Pair<String, SpiExtension>, ConfiguredPluginFactory>()

                    .doImmediatelyOnEachFailure((plugin, err) -> logIndividualFailure(new FailedPlugin(plugin), err))

                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(transformKeys(failures, FailedPlugin::new)));

                    }).build();

    static final FailureHandlingStrategy<ConfiguredPluginFactory, NamedPlugin> PLUGIN_STARTUP_FAILURE_HANDLING_STRATEGY =
            new FailureHandlingStrategy.Builder<ConfiguredPluginFactory, NamedPlugin>()

                    .doImmediatelyOnEachFailure((plugin, err) -> logIndividualFailure(new FailedPlugin(plugin), err))

                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(transformKeys(failures, FailedPlugin::new)));

                    }).build();

    private FailureHandling() {
    }

    private static void logIndividualFailure(FailedPlugin plugin, Exception err) {
        LOGGER.error(format("Could not load plugin: pluginName=%s; factoryClass=%s", plugin.name(), plugin.factoryClass()), err);
    }

    private static <R, K, V> Map<R, V> transformKeys(Map<K, V> map, Function<K, R> transformer) {
        // Preserve order
        Map<R, V> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(transformer.apply(key), value));
        return result;
    }

    private static String afterFailuresErrorMessage(Map<FailedPlugin, Exception> failures) {
        List<String> failedPlugins = failures.keySet().stream()
                .map(FailedPlugin::name)
                .collect(toList());

        List<String> causes = failures.entrySet().stream().map(entry -> {
            String pluginName = entry.getKey().name();
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

    private static class FailedPlugin {
        private final String name;
        private final String factoryClass;

        private FailedPlugin(Pair<String, SpiExtension> plugin) {
            this.name = plugin.key();
            this.factoryClass = plugin.value().factory().factoryClass();
        }

        private FailedPlugin(ConfiguredPluginFactory plugin) {
            this.name = plugin.name();
            this.factoryClass = plugin.pluginFactory().getClass().getName();
        }

        private String name() {
            return name;
        }

        private String factoryClass() {
            return factoryClass;
        }
    }
}
