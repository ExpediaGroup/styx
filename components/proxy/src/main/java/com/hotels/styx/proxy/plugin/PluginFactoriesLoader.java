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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Loads plugin factories.
 */
public class PluginFactoriesLoader {
    private final PluginFactoryLoader pluginFactoryLoader;
    private final FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy;

    /**
     * Creates an instance, using the specified strategy to handle any failures that occur when attempting to load plugins.
     *
     * @param failureHandlingStrategy failure-handling strategy
     */
    public PluginFactoriesLoader(FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy) {
        this(failureHandlingStrategy, new FileSystemPluginFactoryLoader());
    }

    @VisibleForTesting
    private PluginFactoriesLoader(FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy, PluginFactoryLoader pluginFactoryLoader) {
        this.failureHandlingStrategy = requireNonNull(failureHandlingStrategy);
        this.pluginFactoryLoader = requireNonNull(pluginFactoryLoader);
    }

    /**
     * Load plugin factories from plugins metadata.
     *
     * @param pluginsMetadata plugins metadata
     * @return plugin factories
     */
    public List<ConfiguredPluginFactory> load(PluginsMetadata pluginsMetadata) {
        return loadPluginFactories(pluginsMetadata.activePlugins());
    }

    private List<ConfiguredPluginFactory> loadPluginFactories(List<Pair<String, SpiExtension>> inputs) {
        return failureHandlingStrategy.process(inputs, this::loadPluginFactory);
    }

    private ConfiguredPluginFactory loadPluginFactory(Pair<String, SpiExtension> pair) {
        String pluginName = pair.key();
        SpiExtension spiExtension = pair.value();

        PluginFactory factory = pluginFactoryLoader.load(spiExtension);

        return new ConfiguredPluginFactory(pluginName, factory, spiExtension::config);
    }
}
