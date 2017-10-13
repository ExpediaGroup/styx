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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Resource;
import com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.Placeholder;
import com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.UnresolvedPlaceholder;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.common.Logging.sanitise;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.extractPlaceholders;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.replacePlaceholder;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.resolvePlaceholders;
import static java.util.Collections.emptyMap;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Yaml-based configuration object.
 */
public class YamlConfig extends JsonNodeConfig {
    private static final Logger LOGGER = getLogger(YamlConfig.class);

    private static final Map<String, String> NO_OVERRIDES = emptyMap();

    /**
     * Constructs an instance by parsing YAML from a string.
     *
     * @param yaml a YAML string
     */
    public YamlConfig(String yaml) {
        this(yaml, NO_OVERRIDES);
    }

    public YamlConfig(String yaml, Map<String, String> overrides) {
        this(YAML_MAPPER, () -> YAML_MAPPER.readTree(yaml), overrides);
    }

    /**
     * Constructs an instance by parsing YAML from a resource.
     *
     * @param resource resource
     */
    public YamlConfig(Resource resource) {
        this(resource, NO_OVERRIDES);
    }

    /**
     * Constructs an instance by parsing YAML from a resource. Parsed properties can be overridden by
     * supplying alternative values in the overrides map.
     *
     * @param resource  resource
     * @param overrides overrides
     */
    public YamlConfig(Resource resource, Map overrides) {
        this(YAML_MAPPER, provider(resource), overrides);
    }

    /**
     * Constructs an instance by parsing YAML from a resource using the provided mapper.
     * This is useful when using YamlConfig from other JVM languages such as Scala.
     *
     * @param mapper    mapper to be used in de-serialising YAML.
     * @param resource  resource
     * @param overrides overrides
     */
    public YamlConfig(ObjectMapper mapper, Resource resource, Map<String, String> overrides) {
        this(mapper, provider(resource), overrides);
    }

    private static JsonProvider provider(Resource resource) {
        return () -> {
            LOGGER.info("Loading configuration from file: path={}", resource);

            try (InputStream inputStream = resource.inputStream()) {
                return YAML_MAPPER.readTree(inputStream);
            } catch (IOException e) {
                throw propagate(e);
            }
        };
    }

    /**
     * Constructs an instance by parsing YAML from a string using the provided mapper.
     * This is useful when using YamlConfig from other JVM languages such as Scala.
     *
     * @param mapper    mapper to be used in de-serialising YAML.
     * @param yaml      YAML string to be de-serialised.
     * @param overrides overrides
     */
    public YamlConfig(ObjectMapper mapper, String yaml, Map<String, String> overrides) {
        this(mapper, () -> mapper.readTree(yaml), overrides);
    }

    private YamlConfig(ObjectMapper mapper, JsonProvider jsonProvider, Map<String, String> overrides) {
        super(rootNode(jsonProvider, overrides), mapper);
    }

    private static JsonNode rootNode(JsonProvider jsonProvider, Map<String, String> overrides) {
        JsonNode rootNode;

        try {
            JsonNode mainNode = jsonProvider.get();
            rootNode = mergeIfRequired(mainNode, overrides);
        } catch (IOException e) {
            throw propagate(e);
        }

        Collection<UnresolvedPlaceholder> unresolvedPlaceholders = resolvePlaceholders(rootNode, overrides);

        if (!unresolvedPlaceholders.isEmpty()) {
            throw new IllegalStateException("Unresolved placeholders: " + unresolvedPlaceholders);
        }

        applyExternalOverrides(rootNode, overrides);

        return rootNode;
    }

    private static void applyExternalOverrides(JsonNode rootNode, Map<String, String> overrides) {
        overrides.forEach((key, value) -> {
            NodePath nodePath = new NodePath(key);

            nodePath.override(rootNode, value);
        });
    }

    private static JsonNode mergeIfRequired(JsonNode mainNode, Map<String, String> overrides) throws IOException {
        if (mainNode.get("include") != null) {
            resolvePlaceholdersInInclude(mainNode, overrides);

            JsonNode include = mainNode.get("include");

            String includePath = include.textValue();

            getLogger(YamlConfig.class).info("Including config file: path={}", sanitise(includePath));

            JsonNode includedBaseNode = YAML_MAPPER.readTree(newResource(includePath).inputStream());
            includedBaseNode = mergeIfRequired(includedBaseNode, overrides);
            return merge(includedBaseNode, mainNode);
        }
        return mainNode;
    }

    @VisibleForTesting
    static void resolvePlaceholdersInInclude(JsonNode mainNode, Map<String, String> overrides) {
        JsonNode include = mainNode.get("include");
        String textValue = include.textValue();

        textValue = resolvePlaceholdersInText(textValue, overrides);

        ((ObjectNode) mainNode).set("include", TextNode.valueOf(textValue));
    }

    private static String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
        List<Placeholder> placeholders = extractPlaceholders(text);

        String resolvedText = text;

        for (Placeholder placeholder : placeholders) {
            resolvedText = resolvePlaceholderInText(resolvedText, placeholder, overrides);
        }

        return resolvedText;
    }

    private static String resolvePlaceholderInText(String textValue, Placeholder placeholder, Map<String, String> overrides) {
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

    private interface JsonProvider {
        JsonNode get() throws IOException;
    }

    private static JsonNode merge(JsonNode baseNode, JsonNode overrideNode) {
        Iterable<String> fieldNames = overrideNode::fieldNames;

        for (String fieldName : fieldNames) {
            JsonNode jsonNode = baseNode.get(fieldName);

            // if field exists and is an embedded object
            if (jsonNode != null && jsonNode.isObject()) {
                merge(jsonNode, overrideNode.get(fieldName));
            } else {
                if (baseNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = overrideNode.get(fieldName);
                    ((ObjectNode) baseNode).put(fieldName, value);
                }
            }
        }

        return baseNode;
    }
}
