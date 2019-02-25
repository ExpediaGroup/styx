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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.plugins.spi.PluginFactory;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Collects a plugin factory together with its name and a source of configuration.
 * These together allow the plugin to be created and associated with its name.
 */
public class ConfiguredPluginFactory {
    private final String name;
    private final PluginFactory pluginFactory;
    private final Function<Class<?>, Object> configProvider;

    public ConfiguredPluginFactory(String name, PluginFactory pluginFactory, Function<Class<?>, Object> configProvider) {
        this.name = requireNonNull(name);
        this.pluginFactory = requireNonNull(pluginFactory);
        this.configProvider = configProvider == null ? any -> null : configProvider;
    }

    public ConfiguredPluginFactory(String name, PluginFactory pluginFactory) {
        this(name, pluginFactory, null);
    }

    @VisibleForTesting
    public ConfiguredPluginFactory(String name, PluginFactory pluginFactory, Object pluginConfig) {
        this(name, pluginFactory, type -> type.cast(pluginConfig));
    }

    public String name() {
        return name;
    }

    PluginFactory pluginFactory() {
        return pluginFactory;
    }

    <T> T pluginConfig(Class<T> clazz) {
        return (T) configProvider.apply(clazz);
    }
}
