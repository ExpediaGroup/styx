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
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
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
    private final Function<String, ConfigurationSource> sourceFromIncludeFunction;

    private ConfigurationParser(Builder<C> builder) {
        this.format = requireNonNull(builder.format);
        this.overrides = requireNonNull(builder.overrides);
        this.sourceFromIncludeFunction = builder.sourceFromIncludeFunction != null
                ? builder.sourceFromIncludeFunction
                : ConfigurationParser::sourceFromInclude;
    }

    public C parse(ConfigurationSource provider) {
        LOGGER.debug("Parsing configuration in format={} from source={}", format, provider);

        C configuration = doParse(provider);

        PlaceholderResolutionResult<C> resolved = configuration.resolvePlaceholders(overrides);

        checkState(resolved.unresolvedPlaceholders().isEmpty(), "Unresolved placeholders: %s", resolved.unresolvedPlaceholders());

        return resolved.resolvedConfiguration();
    }

    private C doParse(ConfigurationSource provider) {
        C main = provider.deserialise(format);

        C withInclude = includedParentConfig(main)
                .map(main::withParent)
                .orElse(main);

        return withInclude.withOverrides(overrides);
    }

    private Optional<C> includedParentConfig(C main) {
        return main.get("include")
                .map(include -> format.resolvePlaceholdersInText(include, overrides))
                .map(sourceFromIncludeFunction)
                .map(this::doParse);
    }

    private static ConfigurationSource sourceFromInclude(String includePath) {
        LOGGER.info("Including config file: path={}", sanitise(includePath));
        return ConfigurationSource.from(newResource(includePath));
    }

    /**
     * Builder.
     *
     * @param <C> configuration type
     */
    public static final class Builder<C extends ExtensibleConfiguration<C>> {
        private ConfigurationFormat<C> format;
        private Map<String, String> overrides = emptyMap();
        private Function<String, ConfigurationSource> sourceFromIncludeFunction;

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
        Builder<C> sourceFromIncludeFunction(Function<String, ConfigurationSource> sourceFromIncludeFunction) {
            this.sourceFromIncludeFunction = requireNonNull(sourceFromIncludeFunction);
            return this;
        }

        public ConfigurationParser<C> build() {
            return new ConfigurationParser<>(this);
        }
    }
}
