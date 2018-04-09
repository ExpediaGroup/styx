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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class JsonNodeConfigTest {
    @Test
    public void extractsDefaultStringTypeConfigurationFromJsonNode() {
        JsonNode rootNode = jsonNode("{\"text\":\"foo\"}");

        JsonNodeConfig config = new JsonNodeConfig(rootNode);

        assertThat(config.get("text"), isValue("foo"));
        assertThat(config.get("nonExistent"), isAbsent());
    }

    @Test
    public void extractsSpecifiedTypeConfigurationFromJsonNode() {
        JsonNode rootNode = jsonNode("{\"text\":\"foo\",\"number\":123}");

        JsonNodeConfig config = new JsonNodeConfig(rootNode);

        assertThat(config.get("text", String.class), isValue("foo"));
        assertThat(config.get("nonExistent", String.class), isAbsent());

        assertThat(config.get("number", Integer.class), isValue(123));
        assertThat(config.get("nonExistent", Integer.class), isAbsent());
    }

    private static JsonNode jsonNode(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw propagate(e);
        }
    }
}