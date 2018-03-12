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

import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ParserTest {
    @Test
    public void providesConfig() {
        Map<String, Object> values = ImmutableMap.of(
                "foo", 123,
                "bar", "abc");

        StubConfiguration configuration = new StubConfiguration(values);

        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(new StubConfigurationFormat("test-string", configuration))
                .overrides(emptyMap())
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-string"));

        assertThat(parsedConfiguration.get("foo", Integer.class), isValue(123));
        assertThat(parsedConfiguration.get("bar", String.class), isValue("abc"));
    }

    @Test(enabled = false)
    public void includesParent() {
        Map<String, Object> parentValues = ImmutableMap.of(
                "foo", 456,
                "baz", "DEF");

        Map<String, Object> values = ImmutableMap.of(
                "foo", 123,
                "bar", "abc");

        StubConfiguration configuration = new StubConfiguration(values);

        Parser<StubConfiguration> parser = new Parser.Builder<StubConfiguration>()
                .format(new StubConfigurationFormat("test-string", configuration))
                .overrides(emptyMap())
                .build();

        StubConfiguration parsedConfiguration = parser.parse(ConfigurationProvider.from("test-string"));

        assertThat(parsedConfiguration.get("foo", Integer.class), isValue(123));
        assertThat(parsedConfiguration.get("bar", String.class), isValue("abc"));
    }

    private static class StubConfigurationFormat implements ConfigurationFormat<StubConfiguration> {
        private final String expectedString;
        private final StubConfiguration configuration;

        StubConfigurationFormat(String expectedString, StubConfiguration configuration) {
            this.expectedString = expectedString;
            this.configuration = configuration;
        }

        @Override
        public StubConfiguration deserialise(String string) {
            assertThat(string, is(expectedString));
            return configuration;
        }

        @Override
        public StubConfiguration deserialise(Resource resource) {
            return null;
        }
    }

    private static class StubConfiguration implements ExtensibleConfiguration<StubConfiguration> {
        private final Map<String, Object> values;

        StubConfiguration(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public StubConfiguration withParent(StubConfiguration parent) {
            return null;
        }

        @Override
        public <X> Optional<X> get(String key, Class<X> type) {
            return Optional.ofNullable(values.get(key)).map(type::cast);
        }
    }

}