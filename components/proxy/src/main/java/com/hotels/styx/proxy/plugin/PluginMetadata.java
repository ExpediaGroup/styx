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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Objects;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.infrastructure.configuration.ObjectFactory;

import java.io.IOException;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;

class PluginMetadata {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String name;
    private final ObjectFactory factory;
    private final JsonNode config;

    @JsonCreator
    PluginMetadata(@JsonProperty("factory") ObjectFactory factory,
                   @JsonProperty("config") JsonNode config) {
        this.name = null;
        this.factory = factory;
        this.config = config;
    }

    PluginMetadata(String name, ObjectFactory factory, JsonNode config) {
        this.name = name;
        this.factory = factory;
        this.config = config;
    }

    ObjectFactory factory() {
        return factory;
    }

    JsonNode config() {
        return config;
    }

    public PluginFactory newPluginFactory() {
        Optional<PluginFactory> factory = this.factory.newInstance(PluginFactory.class);
        if (!factory.isPresent()) {
            throw new ConfigurationException(format("Could not load a plugin factory for configuration=%s", this));
        }
        return factory.get();
    }

    public String name() {
        return name;
    }

    public <T> T config(Class<T> configClass) {
        JsonParser parser = config.traverse();

        try {
            return MAPPER.readValue(parser, configClass);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    PluginMetadata attachName(String name) {
        return new PluginMetadata(name, factory, config);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, factory);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PluginMetadata other = (PluginMetadata) obj;
        return Objects.equal(this.name, other.name)
                && Objects.equal(this.factory, other.factory);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("factory", factory)
                .toString();
    }
}
