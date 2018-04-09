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
package com.hotels.styx.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.Resource;
import com.hotels.styx.infrastructure.configuration.UnresolvedPlaceholder;
import com.hotels.styx.infrastructure.configuration.yaml.NodePath;
import com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.ByteStreams.toByteArray;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.extractPlaceholders;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.replacePlaceholder;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.resolvePlaceholders;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Reads a YAML file and convert it to specified class.
 * It can handle included files by collecting and adding them to the original set if the class implements
 * {@link com.hotels.styx.infrastructure.Composite} interface.
 *
 * @param <T>
 */
public final class YamlReader<T> {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .configure(AUTO_CLOSE_SOURCE, true);

    private final Map<String, String> overrides;

    public YamlReader() {
        this(emptyMap());
    }

    public YamlReader(Map<String, String> overrides) {
        this.overrides = checkNotNull(overrides);
    }

    public T read(byte[] content, TypeReference<T> typeReference) throws Exception {
        JsonNode rootNode = MAPPER.readTree(content);
        List<String> includePaths = includedPaths(rootNode);

        Collection<UnresolvedPlaceholder> unresolvedPlaceholders = resolvePlaceholders(rootNode, overrides);

        checkState(unresolvedPlaceholders.isEmpty(), "Unresolved placeholders: %s", unresolvedPlaceholders);

        applyExternalOverrides(rootNode, overrides);

        T t = MAPPER.readValue(rootNode.traverse(), typeReference);

        if (t instanceof Composite) {
            for (String includePath : includePaths) {
                t = (T) ((Composite) t).add(read(read(newResource(includePath)), typeReference));
            }
        }

        return t;
    }

    public byte[] read(Resource resource) {
        try {
            return toByteArray(resource.inputStream());
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private List<String> includedPaths(JsonNode rootNode) {
        return Optional.ofNullable(rootNode.get("includes"))
                .map(includes -> {
                    Iterable<JsonNode> elements = includes::elements;

                    List<String> paths = stream(elements.spliterator(), false)
                            .map(includeNode -> resolvePlaceholdersInText(includeNode.textValue(), overrides))
                            .collect(toList());

                    ((ObjectNode) rootNode).remove("includes");

                    return paths;
                })
                .orElse(emptyList());
    }

    private static String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
        List<PlaceholderResolver.Placeholder> placeholders = extractPlaceholders(text);

        String resolvedText = text;

        for (PlaceholderResolver.Placeholder placeholder : placeholders) {
            resolvedText = resolvePlaceholderInText(resolvedText, placeholder, overrides);
        }

        return resolvedText;
    }

    private static String resolvePlaceholderInText(String textValue, PlaceholderResolver.Placeholder placeholder, Map<String, String> overrides) {
        String placeholderName = placeholder.name();

        String override = overrides.get(placeholderName);

        if (override != null) {
            return replacePlaceholder(textValue, placeholderName, override);
        }

        if (placeholder.hasDefaultValue()) {
            return replacePlaceholder(textValue, placeholderName, placeholder.defaultValue());
        }

        throw new IllegalStateException("Cannot resolve placeholder '" + placeholder + "' in include:" + textValue);
    }

    private void applyExternalOverrides(JsonNode rootNode, Map<String, String> overrides) {
        overrides.forEach((key, value) -> {
            NodePath nodePath = new NodePath(key);

            nodePath.override(rootNode, value);
        });
    }
}
