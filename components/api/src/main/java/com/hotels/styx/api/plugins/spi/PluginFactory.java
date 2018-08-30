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
package com.hotels.styx.api.plugins.spi;

/**
 * A factory that creates {@link Plugin}s.
 */
public interface PluginFactory {
    /**
     * Provides global configuration objects for plugins. An example of a configuration object could be a shared
     * {@link com.hotels.styx.api.MetricRegistry}.
     */
    interface Environment extends com.hotels.styx.api.Environment {
        /**
         * Get the plugin's configuration, converted to class {@code clazz}.
         * @param clazz class to convert to
         * @param <T> {@code clazz} as type parameter
         * @return plugin configuration
         */
        <T> T pluginConfig(Class<T> clazz);
    }

    /**
     * Creates a plugin.
     *
     * @return the plugin
     */
    Plugin create(Environment environment);
}
