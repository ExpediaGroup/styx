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

import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.proxy.plugin.PluginStartupException;
import com.hotels.styx.proxy.plugin.PluginSuppliers;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Strategies for handling failure during start-up.
 */
public final class FailureHandling {
    private static final Logger LOG = getLogger(PluginSuppliers.class);

    public static final FailureHandlingStrategy<Pair<String, SpiExtension>, StyxServerComponents.ConfiguredPluginFactory> PLUGIN_FACTORY_LOADING_FAILURE_HANDLING_STRATEGY =
            new FailureHandlingStrategy.Builder<Pair<String, SpiExtension>, StyxServerComponents.ConfiguredPluginFactory>()
                    .doImmediatelyOnEachFailure((plugin, err) ->
                            LOG.error(perFailureErrorMessage(plugin), err))
                    .doOnFailuresAfterAllProcessing(failures -> {
                        throw new PluginStartupException(afterFailuresErrorMessage(failures));
                    }).build();

    private FailureHandling() {
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
