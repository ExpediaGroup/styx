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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
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

    public PluginSuppliers(Environment environment) {
        this(environment, new FileSystemPluginFactoryLoader());
    }

    public PluginSuppliers(Environment environment, PluginFactoryLoader pluginFactoryLoader) {
        this.configuration = environment.configuration();
        this.pluginFactoryLoader = requireNonNull(pluginFactoryLoader);
        this.environment = requireNonNull(environment);
    }

    private Optional<PluginsMetadata> readPluginsConfig() {
        return configuration.get("plugins", PluginsMetadata.class);
    }

    public Iterable<NamedPlugin> fromConfigurations() {
        Iterable<NamedPlugin> plugins = readPluginsConfig()
                .map(this::activePlugins)
                .orElse(emptyList());

        return plugins;
    }

    private Iterable<NamedPlugin> activePlugins(PluginsMetadata pluginsMetadata) {
        List<NamedPlugin> plugins = pluginsMetadata.activePlugins()
                .stream()
                .map(this::loadPlugin)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (pluginsMetadata.activePlugins().size() > plugins.size()) {
            throw new RuntimeException(format("%s plugins could not be loaded", pluginsMetadata.activePlugins().size() - plugins.size()));
        }

        return plugins;
    }

    private Optional<NamedPlugin> loadPlugin(Pair<String, SpiExtension> pair) {
        String pluginName = pair.key();
        SpiExtension spiExtension = pair.value();

        try {
            PluginFactory factory = pluginFactoryLoader.load(spiExtension);
            Plugin plugin = factory.create(new PluginEnvironment(pluginName, environment, spiExtension, DEFAULT_PLUGINS_METRICS_SCOPE));
            return Optional.of(namedPlugin(pluginName, plugin));
        } catch (Throwable e) {
            LOG.error(format("Could not load plugin %s: %s", pluginName, spiExtension.factory().factoryClass()), e);
            return Optional.empty();
        }
    }

}
