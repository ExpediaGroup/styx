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
import com.google.common.collect.ImmutableList;
import com.hotels.styx.infrastructure.configuration.ExtensibleConfiguration.PlaceholderResolutionResult;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.Logging.sanitise;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Generic-parser work-in-progress.
 *
 * @param <C> configuration type
 */
public final class Parser<C extends ExtensibleConfiguration<C>> {
    private static final Logger LOGGER = getLogger(Parser.class);

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

    public ParsingResult<C> parse(ConfigurationProvider provider) {
        C main = deserialise(provider);

        String textRepresentation = main.toString();

        ParsingResult<C> extended = applyParentConfig(main);

        textRepresentation = logChange("Including parent", textRepresentation, extended.configuration());

        ParsingResult<C> withOverrides = applyExternalOverrides(extended);

        textRepresentation = logChange("Overridding properties", textRepresentation, withOverrides.configuration());

        ParsingResult<C> resolved = resolvePlaceholders(withOverrides);

        logChange("Resolving placeholders", textRepresentation, resolved.configuration());

        return resolved;
    }

    private ParsingResult<C> resolvePlaceholders(ParsingResult<C> withOverrides) {
        ParsingResult<C> parsingResult = resolvePlaceholders(withOverrides.configuration());

        Collection<UnresolvedPlaceholder> originalUPs = withOverrides.unresolvedPlaceholders();
        Collection<UnresolvedPlaceholder> newUPs = parsingResult.unresolvedPlaceholders();
        Collection<UnresolvedPlaceholder> finalUPs = new LinkedHashSet<>();
        finalUPs.addAll(originalUPs);
        finalUPs.addAll(newUPs);

        return new ParsingResult<>(parsingResult.configuration(), finalUPs);
    }

    private ParsingResult<C> applyExternalOverrides(ParsingResult<C> extended) {
        return new ParsingResult<>(extended.configuration().withOverrides(overrides), extended.unresolvedPlaceholders());
    }

    private static String logChange(String action, String textRepresentation, Object newTextObject) {
        if (!textRepresentation.equals(newTextObject.toString())) {
            LOGGER.debug("{} changed config from:\n    {}\n        to\n    {}", new Object[]{action, textRepresentation, newTextObject});
            return newTextObject.toString();
        }

        return textRepresentation;
    }


    private ParsingResult<C> applyParentConfig(C main) {
        return main.get("include")
                .map(include -> resolvePlaceholdersInText(include, overrides))
                .map(includePath -> {
                    ParsingResult<C> parentResult = parent(includePath);
                    C parentConfig = parentResult.configuration();
                    return new ParsingResult<>(main.withParent(parentConfig), parentResult.unresolvedPlaceholders());
                })
                .orElse(new ParsingResult<>(main, emptyList()));
    }

    private C deserialise(ConfigurationProvider provider) {
        C main = provider.deserialise(format);

        if (main == null) {
            throw new IllegalStateException("Cannot deserialise from " + provider + " using " + format);
        }

        return main;
    }

    private ParsingResult<C> resolvePlaceholders(C config) {
        PlaceholderResolutionResult<C> result = config.resolvePlaceholders(overrides);

        // TODO put this outside the parser
//        if (!result.unresolvedPlaceholders().isEmpty()) {
//            throw new IllegalStateException("Unresolved placeholders: " + result.unresolvedPlaceholders());
//        }

        return new ParsingResult<>(result);
    }

    private ParsingResult<C> parent(String includePath) {
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
     * Outcome of parsing.
     *
     * @param <C> configuration type
     */
    public static class ParsingResult<C extends ExtensibleConfiguration<C>> {
        private final C configuration;
        private final Collection<UnresolvedPlaceholder> unresolvedPlaceholders;

        private ParsingResult(C configuration, Collection<UnresolvedPlaceholder> unresolvedPlaceholders) {
            this.configuration = requireNonNull(configuration);
            this.unresolvedPlaceholders = ImmutableList.copyOf(unresolvedPlaceholders);
        }

        private ParsingResult(PlaceholderResolutionResult<C> result) {
            this(result.resolvedConfiguration(), result.unresolvedPlaceholders());
        }

        public C configuration() {
            return configuration;
        }

        public Collection<UnresolvedPlaceholder> unresolvedPlaceholders() {
            return unresolvedPlaceholders;
        }
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
