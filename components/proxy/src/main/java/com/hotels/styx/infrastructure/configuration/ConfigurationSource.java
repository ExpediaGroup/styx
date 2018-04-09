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
package com.hotels.styx.infrastructure.configuration;

import com.hotels.styx.api.Resource;

/**
 * Provider of configuration, e.g. file, in-memory string.
 */
public interface ConfigurationSource {
    /**
     * Deserialise configuration that is in a given format.
     *
     * @param format format
     * @param <C> configuration type
     * @return configuration
     */
    <C extends ExtensibleConfiguration<C>> C deserialise(ConfigurationFormat<C> format);

    /**
     * Configuration taken from a string in memory.
     *
     * @param string a string
     * @return configuration provider
     */
    static ConfigurationSource configSource(String string) {
        return new ConfigurationSource() {
            @Override
            public <C extends ExtensibleConfiguration<C>> C deserialise(ConfigurationFormat<C> format) {
                return format.deserialise(string);
            }

            @Override
            public String toString() {
                return "\"" + string + "\"";
            }
        };
    }

    /**
     * Configuration from a resource.
     *
     * @param resource resource
     * @return configuration provider
     */
    static ConfigurationSource configSource(Resource resource) {
        return new ConfigurationSource() {
            @Override
            public <C extends ExtensibleConfiguration<C>> C deserialise(ConfigurationFormat<C> format) {
                return format.deserialise(resource);
            }

            @Override
            public String toString() {
                return resource.toString();
            }
        };
    }
}
