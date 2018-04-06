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


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static java.lang.String.format;
import static java.nio.file.Files.isReadable;

/**
 * System Settings for Styx proxy.
 */
public final class SystemSettings {
    private SystemSettings() {
    }

    /**
     * Return the value associated with the setting.
     *
     * @param setting a setting
     * @param <T>     setting value type
     * @return the value associated with the setting
     * @throws com.hotels.styx.api.configuration.NoSystemPropertyDefined if there is no associated value
     */
    public static <T> T valueOf(Setting<T> setting) {
        return setting.value().orElseThrow(() -> new NoSystemPropertyDefined(setting.name()));
    }

    /**
     * Return the value associated with the setting, or default value if no value is associated.
     *
     * @param setting      a setting
     * @param defaultValue default value
     * @param <T>          setting value type
     * @return the value associated with the setting
     */
    public static <T> T valueOf(Setting<T> setting, T defaultValue) {
        return setting.value().orElse(defaultValue);
    }

    /**
     * Convenient class for implementing settings.
     *
     * @param <T> type of the value to read from system property
     */
    public abstract static class SystemSetting<T> implements Setting<T> {
        private final java.lang.String systemVariable;

        /**
         * Constructs a system setting from the specified system variable.
         *
         * @param systemVariable the system variable
         */
        protected SystemSetting(java.lang.String systemVariable) {
            this.systemVariable = systemVariable;
        }

        @Override
        public Optional<T> value() {
            java.lang.String value = System.getProperty(systemVariable);

            return Optional.ofNullable(value).map(this::convert);
        }

        @Override
        public java.lang.String name() {
            return systemVariable;
        }

        /**
         * Convert the value to the target type <code>T</code>.
         *
         * @param value the value to convert
         * @return the value converted to the target type
         */
        protected abstract T convert(java.lang.String value);
    }

    /**
     * A System setting representing a {@link com.hotels.styx.api.Resource}.
     */
    public static class Resource extends SystemSetting<com.hotels.styx.api.Resource> {
        /**
         * Create a Resource setting from the specified systemVariable.
         *
         * @param systemVariable the variable name to read from
         */
        public Resource(java.lang.String systemVariable) {
            super(systemVariable);
        }

        @Override
        protected com.hotels.styx.api.Resource convert(java.lang.String value) {
            return newResource(value);
        }
    }

    /**
     * A System setting representing a file/directory location.
     */
    public static class Location extends SystemSetting<Path> {
        /**
         * Create a Location setting from the specified systemVariable.
         *
         * @param systemVariable the variable name to read from
         */
        public Location(java.lang.String systemVariable) {
            super(systemVariable);
        }

        @Override
        protected Path convert(java.lang.String value) {
            Path location = Paths.get(removeFilePrefix(value));

            if (!isReadable(location)) {
                throw new ConfigurationException(format("%s=%s is not a readable configuration path.", name(), location));
            }

            return location;
        }

        private java.lang.String removeFilePrefix(java.lang.String value) {
            return value.replaceFirst("file:", "");
        }
    }

    /**
     * Simple string-type System Setting.
     */
    public static class String extends SystemSetting<java.lang.String> {
        /**
         * Creates a String system setting from the specified systemVariable.
         *
         * @param systemVariable the variable name to read from
         */
        public String(java.lang.String systemVariable) {
            super(systemVariable);
        }

        @Override
        protected java.lang.String convert(java.lang.String value) {
            return value;
        }
    }
}
