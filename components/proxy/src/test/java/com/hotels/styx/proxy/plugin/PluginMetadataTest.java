/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
package com.hotels.styx.proxy.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.infrastructure.configuration.ObjectFactory;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PluginMetadataTest {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    public void getsConfigAsClass() throws IOException {
        JsonNode configNode = MAPPER.readTree("foo: bar");

        PluginMetadata metadata = new PluginMetadata("plugin_name", new ObjectFactory("factoryClass", "classPath"), configNode);

        TestObject config = metadata.config(TestObject.class);

        assertThat(config.foo, is("bar"));
    }

    static class TestObject {
        private final String foo;

        public TestObject(@JsonProperty("foo") String foo) {
            this.foo = foo;
        }
    }
}