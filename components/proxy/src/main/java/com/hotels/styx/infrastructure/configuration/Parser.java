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

import com.hotels.styx.api.Resource;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;

import java.util.Map;

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

    private Parser(Builder<C> builder) {
        this.format = requireNonNull(builder.format);
        this.overrides = requireNonNull(builder.overrides);
    }

    public C parse(ConfigurationProvider provider) {
        C main = provider.deserialise(format);

        return main.get("include")
                .map(include -> resolvePlaceholdersInText(include, overrides))
                .map(includePath -> {
                    getLogger(YamlConfig.class).info("Including config file: path={}", sanitise(includePath));

                    Resource includedResource = newResource(includePath);
                    ConfigurationProvider includedConfigurationProvider = ConfigurationProvider.from(includedResource);

                    C includedConfig = parse(includedConfigurationProvider);

                    return main.withParent(includedConfig);
                })
                .orElse(main);
    }

    private static String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
        // TODO finish this
        return text;
    }

    /**
     * Builder.
     *
     * @param <C> configuration type
     */
    public static final class Builder<C extends ExtensibleConfiguration<C>> {
        private ConfigurationFormat<C> format;
        private Map<String, String> overrides;

        public Builder<C> format(ConfigurationFormat<C> format) {
            this.format = requireNonNull(format);
            return this;
        }

        public Builder<C> overrides(Map<String, String> overrides) {
            this.overrides = requireNonNull(overrides);
            return this;
        }

        public Parser<C> build() {
            return new Parser<>(this);
        }
    }
}
