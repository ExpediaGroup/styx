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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.common.Pair.pair;
import static java.util.Objects.requireNonNull;

class PluginsMetadata implements Iterable<SpiExtension> {
    private static final Splitter SPLITTER = Splitter.on(",")
            .omitEmptyStrings()
            .trimResults();
    private final List<String> activePluginsNames;
    private final Map<String, SpiExtension> plugins;

    PluginsMetadata(@JsonProperty("active") String active,
                    @JsonProperty("all") Map<String, SpiExtension> plugins) {
        requireNonNull(active, "No active plugin specified");
        requireNonNull(plugins, "No list of all plugins specified");

        this.activePluginsNames = SPLITTER.splitToList(active);
        this.plugins = plugins;

        plugins.forEach((name, metadata) -> {
            requireNonNull(metadata.factory(), "Factory missing for plugin '" + name + "'");
            requireNonNull(metadata.config(), "Config missing for plugin '" + name + "'");
        });

        activePluginsNames.forEach(name ->
                checkArgument(plugins.containsKey(name), "No such plugin '%s' in %s", name, plugins));
    }

    public List<Pair<String, SpiExtension>> activePlugins() {
        return activePluginsNames.stream()
                .map(name -> pair(name, plugins.get(name)))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("active", activePluginsNames)
                .add("plugins", plugins)
                .toString();
    }

    @Override
    public Iterator<SpiExtension> iterator() {
        return plugins.values().iterator();
    }
}
