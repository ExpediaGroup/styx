/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.common.Pair;
import com.hotels.styx.common.Strings;
import com.hotels.styx.spi.config.SpiExtension;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Pair.pair;
import static com.hotels.styx.common.Preconditions.checkArgument;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Plugins metadata.
 */
public class PluginsMetadata implements Iterable<SpiExtension> {
    private final List<String> activePluginsNames;
    private final List<String> allPluginNames;
    private final Map<String, SpiExtension> plugins;

    PluginsMetadata(@JsonProperty("active") String active,
                    @JsonProperty("all") Map<String, SpiExtension> plugins) {
        requireNonNull(plugins, "No list of all plugins specified");

        this.activePluginsNames = stream((active == null ? "" : active).split(","))
                .map(String::trim)
                .filter(Strings::isNotEmpty)
                .collect(toList());

        this.allPluginNames = listOf(plugins.keySet());
        this.plugins = plugins;

        plugins.forEach((name, metadata) -> {
            requireNonNull(metadata.factory(), "Factory missing for plugin '" + name + "'");
            requireNonNull(metadata.config(), "Config missing for plugin '" + name + "'");
        });

        activePluginsNames.forEach(name ->
                checkArgument(plugins.containsKey(name), "No such plugin '%s' in %s", name, plugins));
    }

    public List<Pair<String, SpiExtension>> plugins() {
        return allPluginNames.stream()
                .map(name -> pair(name, plugins.get(name)))
                .collect(toList());
    }

    public List<Pair<String, SpiExtension>> activePlugins() {
        return activePluginsNames.stream()
                .map(name -> pair(name, plugins.get(name)))
                .collect(toList());
    }

    @Override
    public String toString() {
        return "PluginsMetadata{"
                + "active=" + activePluginsNames
                + ", plugins=" + plugins
                + '}';
    }

    @Override
    public Iterator<SpiExtension> iterator() {
        return plugins.values().iterator();
    }
}
