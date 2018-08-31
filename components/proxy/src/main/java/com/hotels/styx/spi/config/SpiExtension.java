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
package com.hotels.styx.spi.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Objects;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;

/**
 * Factory/configuration block.
 */
public class SpiExtension {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SpiExtensionFactory factory;
    private final JsonNode config;
    private final boolean enabled;

    @JsonCreator
    public SpiExtension(@JsonProperty("factory") SpiExtensionFactory factory,
                        @JsonProperty("config") JsonNode config,
                        @JsonProperty("enabled") Boolean enabled) {
        this.factory = requireNonNull(factory, "Factory attribute missing");
        this.config = requireNonNull(config, "Config attribute missing");
        this.enabled = enabled == null;
    }

    public SpiExtensionFactory factory() {
        return factory;
    }

    public JsonNode config() {
        return config;
    }

    public boolean enabled() {
        return enabled;
    }

    public <T> T config(Class<T> configClass) {
        JsonParser parser = config.traverse();

        try {
            return MAPPER.readValue(parser, configClass);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(factory);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SpiExtension other = (SpiExtension) obj;
        return Objects.equal(this.factory, other.factory);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("factory", factory)
                .toString();
    }
}
