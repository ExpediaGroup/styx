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
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;

import java.util.Map;
import java.util.function.Function;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.Logging.sanitise;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Generic-parser work-in-progress.
 *
 * @param <C> configuration type
 */
public final class Parser<C extends ExtensibleConfiguration<C>> {
    private final ConfigurationFormat<C> format;
    private final Map<String, String> overrides;
    private final Function<String, ConfigurationProvider> includeProviderFunction;

    private Parser(Builder<C> builder) {
        this.format = requireNonNull(builder.format);
        this.overrides = requireNonNull(builder.overrides);
        this.includeProviderFunction = builder.includeProviderFunction != null
                ? builder.includeProviderFunction
                : Parser::includeProvider;
    }

    public C parse(ConfigurationProvider provider) {
        C main = provider.deserialise(format);

        if (main == null) {
            throw new IllegalStateException("Cannot deserialise from " + provider + " using " + format);
        }

        C extended = main.get("include")
                .map(include -> resolvePlaceholdersInText(include, overrides))
                .map(includePath -> main.withParent(parent(includePath)))
                .orElse(main);

        return resolvePlaceholders(extended.withOverrides(overrides));
    }

    private C resolvePlaceholders(C config) {
        if (config.unresolvedPlaceholderCount() == 0) {
            return config;
        }

        int previousUnresolvedPlaceholderCount;

        do {
            previousUnresolvedPlaceholderCount = config.unresolvedPlaceholderCount();
            config = config.resolvePlaceholders();
        } while (config.unresolvedPlaceholderCount() < previousUnresolvedPlaceholderCount);

        return config;
    }

    private C parent(String includePath) {
        ConfigurationProvider includedConfigurationProvider = includeProviderFunction.apply(includePath);

        return parse(includedConfigurationProvider);
    }

    private String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
        return format.resolvePlaceholdersInText(text, overrides);
    }

    private static ConfigurationProvider includeProvider(String includePath) {
        getLogger(YamlConfig.class).info("Including config file: path={}", sanitise(includePath));
        return ConfigurationProvider.from(newResource(includePath));
    }

    /**
     * Builder.
     *
     * @param <C> configuration type
     */
    public static final class Builder<C extends ExtensibleConfiguration<C>> {
        private ConfigurationFormat<C> format;
        private Map<String, String> overrides;
        private Function<String, ConfigurationProvider> includeProviderFunction;

        public Builder<C> format(ConfigurationFormat<C> format) {
            this.format = requireNonNull(format);
            return this;
        }

        public Builder<C> overrides(Map<String, String> overrides) {
            this.overrides = requireNonNull(overrides);
            return this;
        }

        @VisibleForTesting
        Builder<C> includeProviderFunction(Function<String, ConfigurationProvider> includeProviderFunction) {
            this.includeProviderFunction = requireNonNull(includeProviderFunction);
            return this;
        }

        public Parser<C> build() {
            return new Parser<>(this);
        }
    }
}
