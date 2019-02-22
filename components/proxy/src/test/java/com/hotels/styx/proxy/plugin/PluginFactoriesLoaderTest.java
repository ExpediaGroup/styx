/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.common.FailureHandlingStrategy;
import com.hotels.styx.common.Pair;
import com.hotels.styx.spi.config.SpiExtension;
import com.hotels.styx.spi.config.SpiExtensionFactory;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Throwables.propagate;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.testng.Assert.fail;

public class PluginFactoriesLoaderTest {
    @Test
    public void loadsPluginFactories() {
        FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> strategy = new FailureHandlingStrategy.Builder<Pair<String, SpiExtension>, ConfiguredPluginFactory>()
                .build();

        PluginFactoriesLoader loader = new PluginFactoriesLoader(strategy);

        PluginsMetadata metadata = new PluginsMetadata("one, two, three", ImmutableMap.of(
                "one", pluginMetadata(),
                "two", pluginMetadata(),
                "dontLoadThisOne", pluginMetadata(),
                "three", pluginMetadata()
        ));

        List<ConfiguredPluginFactory> factories = loader.load(metadata);

        List<String> names = factories.stream().map(ConfiguredPluginFactory::name).collect(toList());

        assertThat(names, contains("one", "two", "three"));
    }

    @Test
    public void handlesFailures() {
        List<String> individualFailures = new ArrayList<>();
        List<String> allFailures = new ArrayList<>();

        FailureHandlingStrategy<Pair<String, SpiExtension>, ConfiguredPluginFactory> strategy = new FailureHandlingStrategy.Builder<Pair<String, SpiExtension>, ConfiguredPluginFactory>()
                .doImmediatelyOnEachFailure((plugin, err) -> individualFailures.add(plugin.key()))
                .doOnFailuresAfterAllProcessing(failures -> failures.forEach((plugin, err) -> allFailures.add(plugin.key())))
                .build();

        PluginFactoriesLoader loader = new PluginFactoriesLoader(strategy);

        PluginsMetadata metadata = new PluginsMetadata("bad1, good, bad2", ImmutableMap.of(
                "bad1", badPluginMetadata(),
                "good", pluginMetadata(),
                "bad2", badPluginMetadata()
        ));

        List<ConfiguredPluginFactory> factories = loader.load(metadata);

        assertThat(individualFailures, contains("bad1", "bad2"));
        assertThat(allFailures, contains("bad1", "bad2"));

        List<String> names = factories.stream().map(ConfiguredPluginFactory::name).collect(toList());

        assertThat(names, contains("good"));
    }

    private static SpiExtension pluginMetadata() {
        return new SpiExtension(factory(), config(), true);
    }

    private static SpiExtension badPluginMetadata() {
        return new SpiExtension(badFactory(), config(), true);
    }

    private static SpiExtensionFactory factory() {
        return new SpiExtensionFactory(StubPluginFactory.class.getName(), "/some/class/path");
    }

    private static SpiExtensionFactory badFactory() {
        return new SpiExtensionFactory("notARealClass", "/some/class/path");
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

    public static class StubPluginFactory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            fail("Unexpected execution of plugin factory");
            return null;
        }
    }

}