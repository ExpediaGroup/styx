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

import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;
import com.hotels.styx.startup.StyxServerComponents.ConfiguredPluginFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Loads plugin factories.
 */
public class PluginFactoriesLoader {
    private final PluginFactoryLoader pluginFactoryLoader;

    private final FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy;

    public PluginFactoriesLoader(FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy) {
        this(failureHandlingStrategy, new FileSystemPluginFactoryLoader());
    }

    PluginFactoriesLoader(FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> failureHandlingStrategy, PluginFactoryLoader pluginFactoryLoader) {
        this.failureHandlingStrategy = requireNonNull(failureHandlingStrategy);
        this.pluginFactoryLoader = requireNonNull(pluginFactoryLoader);
    }

    public Iterable<ConfiguredPluginFactory> load(PluginsMetadata pluginsMetadata) {
        return loadPluginFactories(pluginsMetadata.activePlugins());
    }

    private Iterable<ConfiguredPluginFactory> loadPluginFactories(List<Pair<String, SpiExtension>> inputs) {
        return failureHandlingStrategy.process(inputs, this::loadPluginFactory);
    }

    private ConfiguredPluginFactory loadPluginFactory(Pair<String, SpiExtension> pair) {
        String pluginName = pair.key();
        SpiExtension spiExtension = pair.value();

        PluginFactory factory = pluginFactoryLoader.load(spiExtension);

        return new ConfiguredPluginFactory(pluginName, factory, spiExtension::config);
    }
}
