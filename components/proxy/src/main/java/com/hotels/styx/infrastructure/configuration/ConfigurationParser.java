/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.infrastructure.configuration;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.infrastructure.configuration.ExtensibleConfiguration.PlaceholderResolutionResult;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.Logging.sanitise;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration parser.
 *
 * @param <C> configuration type
 */
public final class ConfigurationParser<C extends ExtensibleConfiguration<C>> {
    private static final Logger LOGGER = getLogger(ConfigurationParser.class);

    private final ConfigurationFormat<C> format;
    private final Map<String, String> overrides;
    private final Function<String, ConfigurationProvider> includeProviderFunction;

    private ConfigurationParser(Builder<C> builder) {
        this.format = requireNonNull(builder.format);
        this.overrides = requireNonNull(builder.overrides);
        this.includeProviderFunction = builder.includeProviderFunction != null
                ? builder.includeProviderFunction
                : ConfigurationParser::includeProvider;
    }

    public C parse(ConfigurationProvider provider) {
        LOGGER.debug("Parsing configuration in format={} from source={}", format, provider);

        C configuration = doParse(provider);

        PlaceholderResolutionResult<C> resolved = configuration.resolvePlaceholders(overrides);

        if (!resolved.unresolvedPlaceholders().isEmpty()) {
            throw new IllegalStateException("Unresolved placeholders: " + resolved.unresolvedPlaceholders());
        }

        return resolved.resolvedConfiguration();
    }

    private C doParse(ConfigurationProvider provider) {
        C main = provider.deserialise(format);
        C extended = applyParentConfig(main);

        return extended.withOverrides(overrides);
    }

    private C applyParentConfig(C main) {
        return main.get("include")
                .map(include -> format.resolvePlaceholdersInText(include, overrides))
                .map(this::parent)
                .map(main::withParent)
                .orElse(main);
    }

    private C parent(String includePath) {
        ConfigurationProvider includedConfigurationProvider = includeProviderFunction.apply(includePath);

        return doParse(includedConfigurationProvider);
    }

    private static ConfigurationProvider includeProvider(String includePath) {
        LOGGER.info("Including config file: path={}", sanitise(includePath));
        return ConfigurationProvider.from(newResource(includePath));
    }

    /**
     * Builder.
     *
     * @param <C> configuration type
     */
    public static final class Builder<C extends ExtensibleConfiguration<C>> {
        private ConfigurationFormat<C> format;
        private Map<String, String> overrides = emptyMap();
        private Function<String, ConfigurationProvider> includeProviderFunction;

        public Builder<C> format(ConfigurationFormat<C> format) {
            this.format = requireNonNull(format);
            return this;
        }

        public Builder<C> overrides(Map<String, String> overrides) {
            this.overrides = requireNonNull(overrides);
            return this;
        }

        public Builder<C> overrides(Properties properties) {
            return overrides((Map) properties);
        }

        @VisibleForTesting
        Builder<C> includeProviderFunction(Function<String, ConfigurationProvider> includeProviderFunction) {
            this.includeProviderFunction = requireNonNull(includeProviderFunction);
            return this;
        }

        public ConfigurationParser<C> build() {
            return new ConfigurationParser<>(this);
        }
    }
}
