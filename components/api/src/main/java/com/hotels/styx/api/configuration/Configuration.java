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
package com.hotels.styx.api.configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Static configuration.
 */
public interface Configuration {

    /**
     * Returns the value to which the specified key is mapped, converted to String
     * or {@code Optional.absent} if this configuration source contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     * {@code Optional.absent} if this map contains no mapping for the key
     */
    default Optional<String> get(String key) {
        return get(key, String.class);
    }

    /**
     * Returns the value to which the specified key is mapped, converted to the target type specified
     * or {@code Optional.absent} if this configuration source contains no mapping for the key.
     *
     * @param <X>  type of the configuration value
     * @param key  the key whose associated value is to be returned
     * @param type the target type
     * @return the value to which the specified key is mapped, or
     * {@code Optional.absent} if this map contains no mapping for the key
     */
    <X> Optional<X> get(String key, Class<X> type);

    default <X> X as(Class<X> type) throws ConversionException {
        throw new ConversionException("Cannot convert self to " + type);
    }

    Configuration EMPTY_CONFIGURATION = new Configuration() {
        @Override
        public <X> Optional<X> get(String key, Class<X> type) {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "{}";
        }
    };

    /**
     * Allows a configuration object to be programmatically created. Primarily intended for testing.
     */
    class MapBackedConfiguration implements Configuration {
        private final Configuration parent;
        private final Map<String, Object> config = new HashMap<>();

        public MapBackedConfiguration() {
            this(EMPTY_CONFIGURATION);
        }

        public MapBackedConfiguration(Configuration parent) {
            this.parent = requireNonNull(parent);
        }

        public MapBackedConfiguration set(String key, Object object) {
            requireNonNull(key);
            config.put(key, object);
            return this;
        }

        @Override
        public <X> Optional<X> get(String key, Class<X> type) {
            Optional<X> found = Optional.ofNullable(config.get(key)).map(type::cast);

            if (!found.isPresent()) {
                return parent.get(key, type);
            }

            return found;
        }

        @Override
        public String toString() {
            return "{}";
        }
    }

    /**
     * This should be removed if possible, as its creation was premature - intended for a use-case that we have
     * not implemented. If we remove this, we should also be able to remove AggregatedConfiguration.
     */
    interface Context {
        Map<String, String> asMap();

        Context EMPTY_CONFIGURATION_CONTEXT = Collections::emptyMap;
    }
}
