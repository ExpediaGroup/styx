/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hotels.styx.common.MapStream.stream;
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

                    .doImmediatelyOnEachFailure((plugin, err) ->
                            LOGGER.error(format("Could not load plugin: pluginName=%s; factoryClass=%s", plugin.key(), plugin.value().factory().factoryClass()), err))

                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(failures, Pair::key));

                    }).build();

    static final FailureHandlingStrategy<ConfiguredPluginFactory, NamedPlugin> PLUGIN_STARTUP_FAILURE_HANDLING_STRATEGY =
            new FailureHandlingStrategy.Builder<ConfiguredPluginFactory, NamedPlugin>()

                    .doImmediatelyOnEachFailure((plugin, err) ->
                            LOGGER.error(format("Could not load plugin: pluginName=%s; factoryClass=%s", plugin.name(), plugin.pluginFactory().getClass().getName()), err))

                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(failures, ConfiguredPluginFactory::name));

                    }).build();

    private FailureHandling() {
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
