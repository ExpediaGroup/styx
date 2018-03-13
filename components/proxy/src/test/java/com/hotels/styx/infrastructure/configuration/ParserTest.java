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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Resource;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

public class ParserTest {
    private final StubConfigSources config = new StubConfigSources()
            .plus("test-config", ImmutableMap.of(
                    "include", "parent-config-source",
                    "number", 123,
                    "string", "abc",
                    "numberFromParent", 999,
                    "hasPlaceholder", "${string}"
            ))

            .plus("test-parent-config", ImmutableMap.of(
                    "numberFromParent", 111,
                    "stringFromParent", "DEF"));

    @Test
    public void providesConfig() {
        StubConfigSources configWithoutIncludes = new StubConfigSources()
                .plus("test-config", ImmutableMap.of(
                        "foo", 123,
                        "bar", "abc"));

        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(format(configWithoutIncludes))
                .overrides(emptyMap())
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-config"));

        assertThat(parsedConfiguration.get("bar"), isValue("abc"));
        assertThat(parsedConfiguration.get("foo", Integer.class), isValue(123));
    }

    @Test
    public void includesParent() {
        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(format(config))
                .includeProviderFunction(includedConfigProvider("parent-config-source", "test-parent-config"))
                .overrides(emptyMap())
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-config"));

        // Present in child only
        assertThat(parsedConfiguration.get("string"), isValue("abc"));

        // Present in parent, not present in child
        assertThat(parsedConfiguration.get("stringFromParent"), isValue("DEF"));

        // Present in parent, overridden by child
        assertThat(parsedConfiguration.get("numberFromParent", Integer.class), isValue(999));
    }

    @Test
    public void resolvesPlaceholdersInConfig() {
        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(format(config))
                .includeProviderFunction(includedConfigProvider("parent-config-source", "test-parent-config"))
                .overrides(emptyMap())
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-config"));

        assertThat(parsedConfiguration.get("hasPlaceholder"), isValue("abc"));
    }

    @Test
    public void appliesOverrides() {
        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(format(config))
                .includeProviderFunction(includedConfigProvider("parent-config-source", "test-parent-config"))
                .overrides(ImmutableMap.of(
                        "not-present-in-original", "foo-bar",
                        "string", "overridden"
                        ))
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-config"));

        assertThat(parsedConfiguration.get("not-present-in-original"), isValue("foo-bar"));
        assertThat(parsedConfiguration.get("string"), isValue("overridden"));
    }

    // TODO test placeholder in "include" value

    private static Function<String, ConfigurationProvider> includedConfigProvider(String source, String providedString) {
        return actualSource -> {
            assertThat(actualSource, is(source));
            return ConfigurationProvider.from(providedString);
        };
    }

    private static class StubConfiguration implements ExtensibleConfiguration<StubConfiguration> {
        private final Map<String, Object> values;

        StubConfiguration(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public StubConfiguration withParent(StubConfiguration parent) {
            Map<String, Object> newValues = new HashMap<>(parent.values);
            newValues.putAll(this.values);
            return new StubConfiguration(newValues);
        }

        @Override
        public StubConfiguration withOverrides(Map<String, String> overrides) {
            Map<String, Object> newValues = new HashMap<>(this.values);
            newValues.putAll(overrides);
            return new StubConfiguration(newValues);
        }

        @Override
        public int unresolvedPlaceholderCount() {
            return (int) values.values().stream()
                    .filter(object -> object instanceof String)
                    .map(String.class::cast)
                    .filter(value -> Objects.equals(value, "${string}"))
                    .count();
        }

        @Override
        public StubConfiguration resolvePlaceholders() {
            Map<String, Object> resolved = values.entrySet().stream().collect(toMap(
                    Map.Entry::getKey,
                    entry -> resolve(entry.getValue())
            ));

            return new StubConfiguration(resolved);
        }

        private Object resolve(Object original) {
            if (Objects.equals(original, "${string}")) {
                return values.get("string");
            }

            return original;
        }

        @Override
        public <X> Optional<X> get(String key, Class<X> type) {
            return Optional.ofNullable(values.get(key)).map(type::cast);
        }
    }

    private static ConfigurationFormat<StubConfiguration> format(StubConfigSources configurations) {
        return new ConfigurationFormat<StubConfiguration>() {
            @Override
            public StubConfiguration deserialise(String string) {
                return configurations.config(string);
            }

            @Override
            public StubConfiguration deserialise(Resource resource) {
                fail("Unexpected method call with arg " + resource);
                return null;
            }
        };
    }

    private static class StubConfigSources {
        private final Map<String, StubConfiguration> configurations;

        StubConfigSources() {
            this.configurations = new HashMap<>();
        }

        StubConfigSources plus(String source, Map<String, Object> values) {
            configurations.put(source, new StubConfiguration(values));
            return this;
        }

        StubConfiguration config(String source) {
            return configurations.get(source);
        }
    }
}