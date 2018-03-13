/**
 * Copyright (C) 2013-2018 Expedia Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.infrastructure.configuration;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.UnresolvedPlaceholder;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Extensible configuration, interface is agnostic to how it is stored in memory.
 *
 * @param <C> its own type
 */
public interface ExtensibleConfiguration<C extends ExtensibleConfiguration<C>> extends Configuration {
    C withParent(C parent);

    C withOverrides(Map<String, String> overrides);

    PlaceholderResolutionResult<C> resolvePlaceholders();

    class PlaceholderResolutionResult<C extends ExtensibleConfiguration<C>> {
        private final C resolvedConfiguration;
        private final Collection<UnresolvedPlaceholder> unresolvedPlaceholders;

        public PlaceholderResolutionResult(C resolvedConfiguration, Collection<UnresolvedPlaceholder> unresolvedPlaceholders) {
            this.resolvedConfiguration = Objects.requireNonNull(resolvedConfiguration);
            this.unresolvedPlaceholders = ImmutableList.copyOf(unresolvedPlaceholders);
        }

        public C resolvedConfiguration() {
            return resolvedConfiguration;
        }

        public Collection<UnresolvedPlaceholder> unresolvedPlaceholders() {
            return unresolvedPlaceholders;
        }
    }
}
