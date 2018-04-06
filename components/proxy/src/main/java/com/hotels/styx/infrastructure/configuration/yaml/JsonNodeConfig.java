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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.configuration.ConversionException;
import com.hotels.styx.api.configuration.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;

/**
 * Configuration from a jackson JSON node. This class does not do any JSON parsing - it is just a bridge between the
 * jackson API and Styx API.
 */
public class JsonNodeConfig implements Configuration {
    static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final ObjectMapper mapper;
    private final JsonNode rootNode;

    /**
     * Construct an instance from a JSON node.
     *
     * @param rootNode a JSON node
     */
    public JsonNodeConfig(JsonNode rootNode) {
        this(rootNode, YAML_MAPPER);
    }

    /**
     * Construct an instance from a JSON node.
     *
     * @param rootNode a JSON node
     * @param mapper mapper to convert JSON into objects
     */
    protected JsonNodeConfig(JsonNode rootNode, ObjectMapper mapper) {
        this.rootNode = requireNonNull(rootNode);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Optional<String> get(String key) {
        return get(key, String.class);
    }

    @Override
    public <T> Optional<T> get(String property, Class<T> tClass) {
        return nodeAt(property)
                .map(node -> {
                    if (tClass == Path.class) {
                        return (T) Paths.get(node.textValue());
                    }

                    return parseNodeToClass(node, tClass);
                });
    }

    private Optional<JsonNode> nodeAt(String property) {
        NodePath nodePath = new NodePath(property);

        return nodePath.findMatchingDescendant(rootNode);
    }

    private <T> T parseNodeToClass(JsonNode node, Class<T> tClass) {
        JsonParser parser = node.traverse();

        try {
            return mapper.readValue(parser, tClass);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    @Override
    public <X> X as(Class<X> type) throws ConversionException {
        return parseNodeToClass(rootNode, type);
    }

    @Override
    public String toString() {
        return rootNode.toString();
    }
}
