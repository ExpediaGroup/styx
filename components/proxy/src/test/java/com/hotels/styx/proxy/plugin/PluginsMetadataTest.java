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
package com.hotels.styx.proxy.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtensionFactory;
import com.hotels.styx.spi.config.SpiExtension;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Throwables.propagate;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class PluginsMetadataTest {
    private int fakeClassNumber;

    @Test
    public void pluginsAreProvidedInOrderSpecifiedByActivePluginsCommaSeparatedList() {
        // LinkedHashMap preserves insertion order
        Map<String, SpiExtension> plugins = new LinkedHashMap<>();

        Stream.of("two", "four", "three", "one")
                .forEach(key -> plugins.put(key, pluginMetadata()));

        PluginsMetadata pluginsMetadata = new PluginsMetadata("one,two,three,four", plugins);

        List<String> activePlugins = pluginsMetadata.activePlugins().stream()
                .map(Pair::key)
                .collect(toList());

        assertThat(activePlugins, contains("one", "two", "three", "four"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwsExceptionIfActivePluginDoesNotExist() throws IOException {
        Map<String, SpiExtension> plugins = ImmutableMap.of(
                "one", pluginMetadata(),
                "two", pluginMetadata()
        );

        new PluginsMetadata("one,monkey,two", plugins);
    }

    private SpiExtension pluginMetadata() {
        return new SpiExtension(factory(), config(), true);
    }

    private SpiExtensionFactory factory() {
        return new SpiExtensionFactory("fakeclass.FakeClass" + fakeClassNumber++, "/some/class/path");
    }

    private static JsonNode config() {
        try {
            return new ObjectMapper(new YAMLFactory())
                    .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readTree("foo: bar");
        } catch (IOException e) {
            throw propagate(e);
        }
    }
}