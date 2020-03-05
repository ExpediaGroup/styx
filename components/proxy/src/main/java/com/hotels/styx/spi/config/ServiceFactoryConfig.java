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
package com.hotels.styx.spi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;

import static java.util.Objects.requireNonNull;

/**
 * A class, intended to be produced by a YAML-parser, that will configure a factory.
 */
public class ServiceFactoryConfig {
    private final boolean enabled;
    private final String factory;
    private final JsonNodeConfig config;

    ServiceFactoryConfig(@JsonProperty("enabled") Boolean enabled,
                         @JsonProperty("class") String factory,
                         @JsonProperty("config") JsonNode config) {
        this.enabled = enabled == null ? true : enabled;
        this.factory = requireNonNull(factory);
        this.config = new JsonNodeConfig(config);
    }

    public String factory() {
        return factory;
    }

    public JsonNodeConfig config() {
        return config;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append(this.getClass().getSimpleName());
        sb.append("{factory=");
        sb.append(factory);
        sb.append(", config=");
        sb.append(config);
        return sb.append('}').toString();

    }
}
