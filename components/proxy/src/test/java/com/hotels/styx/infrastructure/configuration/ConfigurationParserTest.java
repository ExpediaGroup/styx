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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Resource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.replacePlaceholder;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigurationParserTest {
    private final StubFormat format = new StubFormat();
    private Map<String, StubConfiguration> fakeFileSystem;

    @BeforeMethod
    public void setUp() {
        fakeFileSystem = new HashMap<>();

        createStubConfig("/fake/base-config.yml", ImmutableMap.of(
                "include", "/fake/parent-config.yml",
                "number", 123,
                "string", "abc",
                "numberFromParent", 999,
                "hasPlaceholder", "${string}"
        ));

        createStubConfig("/fake/parent-config.yml", ImmutableMap.of(
                "numberFromParent", 111,
                "stringFromParent", "DEF"));
    }

    @Test
    public void providesConfig() {
        createStubConfig("/fake/simple-config.yml", ImmutableMap.of(
                "foo", 123,
                "bar", "abc"));

        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/fake/simple-config.yml")));

        assertThat(parsedConfiguration.get("bar"), isValue("abc"));
        assertThat(parsedConfiguration.get("foo", Integer.class), isValue(123));
    }

    @Test
    public void includesParent() {
        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/fake/base-config.yml")));

        // Present in child only
        assertThat(parsedConfiguration.get("string"), isValue("abc"));

        // Present in parent, not present in child
        assertThat(parsedConfiguration.get("stringFromParent"), isValue("DEF"));

        // Present in parent, overridden by child
        assertThat(parsedConfiguration.get("numberFromParent", Integer.class), isValue(999));
    }

    @Test
    public void includedValuesCanBeOverridden() {
        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/fake/base-config.yml")));

        // Present in child only
        assertThat(parsedConfiguration.get("string"), isValue("abc"));

        // Present in parent, not present in child
        assertThat(parsedConfiguration.get("stringFromParent"), isValue("DEF"));

        // Present in parent, overridden by child
        assertThat(parsedConfiguration.get("numberFromParent", Integer.class), isValue(999));
    }

    @Test
    public void resolvesPlaceholdersInConfig() {
        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/fake/base-config.yml")));

        assertThat(parsedConfiguration.get("hasPlaceholder"), isValue("abc"));
    }

    @Test
    public void appliesOverrides() {
        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .overrides(ImmutableMap.of("string", "overridden"))
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/fake/base-config.yml")));

        assertThat(parsedConfiguration.get("string"), isValue("overridden"));
    }

    @Test
    public void includeValueCanContainPlaceholder() {
        createStubConfig("/test/base-config.yml", ImmutableMap.of(
                "include", "${include-placeholder}",
                "number", 123,
                "string", "abc",
                "numberFromParent", 999,
                "hasPlaceholder", "${string}"
        ));

        createStubConfig("/test/parent-config.yml", ImmutableMap.of(
                "numberFromParent", 111,
                "stringFromParent", "DEF"));

        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .overrides(ImmutableMap.of("include-placeholder", "/test/parent-config.yml"))
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/test/base-config.yml")));

        assertThat(parsedConfiguration.get("stringFromParent"), isValue("DEF"));
    }

    @Test
    public void childCanReplaceParentPlaceholders() {
        createStubConfig("/test/base-config.yml", ImmutableMap.of(
                "include", "/test/parent-config.yml",
                "childString", "abc"
        ));

        createStubConfig("/test/parent-config.yml", ImmutableMap.of(
                "parentString", "${childString}"
        ));

        ConfigurationParser<StubConfiguration> parser = new ConfigurationParser.Builder<StubConfiguration>()
                .format(format)
                .build();

        StubConfiguration parsedConfiguration = parser.parse(configSource(newResource("/test/base-config.yml")));

        assertThat(parsedConfiguration.get("parentString"), isValue("abc"));
    }

    private void createStubConfig(String path, Map<String, Object> config) {
        fakeFileSystem.put(path, new StubConfiguration(config));
    }

    private static class StubConfiguration implements ExtensibleConfiguration<StubConfiguration> {
        private final Map<String, Object> values;

        StubConfiguration(Map<String, Object> values) {
            this.values = new TreeMap<>(values);
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
        public PlaceholderResolutionResult<StubConfiguration> resolvePlaceholders(Map<String, String> overrides) {
            Map<String, Object> resolved = values.entrySet().stream().collect(toMap(
                    Map.Entry::getKey,
                    entry -> resolve(entry.getValue())
            ));

            return new PlaceholderResolutionResult<>(new StubConfiguration(resolved), emptyList());
        }

        private Object resolve(Object original) {
            if (!(original instanceof String)) {
                return original;
            }

            String os = String.valueOf(original);

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value != null) {
                    os = replacePlaceholder(os, key, value.toString());
                }
            }

            return os;
        }

        @Override
        public <X> Optional<X> get(String key, Class<X> type) {
            return Optional.ofNullable(values.get(key)).map(type::cast);
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private class StubFormat implements ConfigurationFormat<StubConfiguration> {
        @Override
        public StubConfiguration deserialise(String string) {
            // Not used in test
            throw new UnsupportedOperationException();
        }

        @Override
        public StubConfiguration deserialise(Resource resource) {
            return requireNonNull(fakeFileSystem.get(resource.path()));
        }

        @Override
        public String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
            String resolved = text;

            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
            }

            return resolved;
        }

        @Override
        public String toString() {
            return "Stub";
        }
    }
}