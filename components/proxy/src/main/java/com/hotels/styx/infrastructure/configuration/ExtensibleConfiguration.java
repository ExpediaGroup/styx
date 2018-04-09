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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.configuration.Configuration;

import java.util.Collection;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Extensible configuration, interface is agnostic to how it is stored in memory.
 *
 * @param <C> its own type
 */
public interface ExtensibleConfiguration<C extends ExtensibleConfiguration<C>> extends Configuration {
    /**
     * Merges this configuration with parent configuration.
     *
     * @param parent parent configuration
     * @return merged configuration
     */
    C withParent(C parent);

    /**
     * Overrides configuration properties with mapped values.
     *
     * @param overrides override values
     * @return overridden configuration
     */
    C withOverrides(Map<String, String> overrides);

    /**
     * Resolves placeholders in this configuration, using both its own properties and overrides.
     *
     * @param overrides override values
     * @return result of resolution
     */
    PlaceholderResolutionResult<C> resolvePlaceholders(Map<String, String> overrides);

    /**
     * The outcome of resolving placeholders. It is possible that not all placeholders will be resolved,
     * due to having no available value, and these unresolved placeholders are made available alongside the
     * resolved configuration.
     *
     * @param <C> the configuration type
     */
    class PlaceholderResolutionResult<C extends ExtensibleConfiguration<C>> {
        private final C resolvedConfiguration;
        private final Collection<UnresolvedPlaceholder> unresolvedPlaceholders;

        public PlaceholderResolutionResult(C resolvedConfiguration, Collection<UnresolvedPlaceholder> unresolvedPlaceholders) {
            this.resolvedConfiguration = requireNonNull(resolvedConfiguration);
            this.unresolvedPlaceholders = ImmutableList.copyOf(unresolvedPlaceholders);
        }

        /**
         * Configuration with placeholders resolved.
         *
         * @return resolved configuration
         */
        public C resolvedConfiguration() {
            return resolvedConfiguration;
        }

        /**
         * Placeholders that could not be resolved.
         *
         * @return unresolved placeholders
         */
        public Collection<UnresolvedPlaceholder> unresolvedPlaceholders() {
            return unresolvedPlaceholders;
        }
    }
}
