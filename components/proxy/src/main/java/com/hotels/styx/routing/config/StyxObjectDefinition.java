/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.routing.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;

import java.util.List;

import static com.hotels.styx.common.Collections.listOf;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * An yaml configuration block used in routing configuration to configure an HTTP handler.
 */
@JsonDeserialize(builder = StyxObjectDefinition.Builder.class)
public class StyxObjectDefinition implements StyxObjectConfiguration {
    private final String name;
    private final String type;
    private final List<String> tags;
    private final JsonNode config;

    public StyxObjectDefinition(String name, String type, List<String> tags, JsonNode config) {
        this.name = requireNonNull(name);
        this.type = requireNonNull(type);
        this.tags = listOf(tags);
        this.config = requireNonNull(config);
    }

    public StyxObjectDefinition(String name, String type, JsonNode config) {
        this(name, type, emptyList(), config);
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public List<String> tags() {
        return tags;
    }

    public JsonNode config() {
        return config;
    }

    public <T> T config(Class<T> tClass) {
        return new JsonNodeConfig(this.config).as(tClass);
    }

    static class Builder {
        private List<String> tags = emptyList();
        private String name = "";

        private JsonNode config;
        private String type;

        @JsonProperty("name")
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @JsonProperty("type")
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        @JsonProperty("tags")
        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        @JsonProperty("config")
        public Builder config(JsonNode config) {
            this.config = config;
            return this;
        }

        public StyxObjectDefinition build() {
            return new StyxObjectDefinition(name, type, tags, config);
        }
    }
}
