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
package com.hotels.styx.routing.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;

/**
 * An yaml configuration block used in routing configuration to configure an HTTP handler.
 */
@JsonDeserialize(builder = RouteHandlerDefinition.Builder.class)
public class RouteHandlerDefinition implements RouteHandlerConfig {
    private final String name;
    private final String type;
    private final JsonNode config;

    public RouteHandlerDefinition(String name, String type, JsonNode config) {
        this.name = name;
        this.type = type;
        this.config = config;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public JsonNode config() {
        return config;
    }

    public <T> T config(Class<T> tClass) {
        return new JsonNodeConfig(this.config).as(tClass);
    }

    static class Builder {
        private JsonNode config;
        private String type;
        private String name;

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

        @JsonProperty("config")
        public Builder config(JsonNode config) {
            this.config = config;
            return this;
        }

        public RouteHandlerDefinition build() {
            return new RouteHandlerDefinition(name, type, config);
        }
    }
}
