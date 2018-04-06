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

import java.util.Map;

/**
 * Format for configuration, e.g. JSON, YAML.
 *
 * @param <C> configuration type
 */
public interface ConfigurationFormat<C extends ExtensibleConfiguration<C>> {
    /**
     * Deserialise configuration from a literal string. This is primarily intended for tests,
     * but could be used whenever the configuration has already been loaded.
     *
     * @param string string
     * @return configuration
     */
    C deserialise(String string);

    /**
     * Deserialise configuration from a resource.
     *
     * @param resource resource
     * @return configuration
     */
    C deserialise(Resource resource);

    /**
     * Resolves placeholders in a piece of text, using only overrides, not loaded configuration.
     *
     * @param text text
     * @param overrides overrides
     * @return text with resolved placeholders
     */
    String resolvePlaceholdersInText(String text, Map<String, String> overrides);
}
