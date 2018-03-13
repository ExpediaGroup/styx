package com.hotels.styx.infrastructure.configuration.yaml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotels.styx.api.configuration.ConversionException;
import com.hotels.styx.infrastructure.configuration.ExtensibleConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML_MAPPER;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class YamlConfiguration implements ExtensibleConfiguration<YamlConfiguration> {
    private final JsonNode rootNode;
    private final int unresolvedPlaceholders;

    public YamlConfiguration(JsonNode rootNode) {
        this.rootNode = requireNonNull(rootNode);
        this.unresolvedPlaceholders = countUnresolvedPlaceholders();
    }

    private YamlConfiguration(JsonNode rootNode, int unresolvedPlaceholders) {
        this.rootNode = requireNonNull(rootNode);
        this.unresolvedPlaceholders = unresolvedPlaceholders;
    }

    private static int countUnresolvedPlaceholders() {
        return 0;
    }

    @Override
    public YamlConfiguration withParent(YamlConfiguration parent) {
        return new YamlConfiguration(merge(parent.rootNode.deepCopy(), rootNode));
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

    @Override
    public YamlConfiguration withOverrides(Map<String, String> overrides) {
        JsonNode newRootNode = rootNode.deepCopy();

        applyExternalOverrides(newRootNode, overrides);

        return new YamlConfiguration(newRootNode, unresolvedPlaceholders);
    }

    private static void applyExternalOverrides(JsonNode rootNode, Map<String, String> overrides) {
        overrides.forEach((key, value) -> {
            NodePath nodePath = new NodePath(key);

            nodePath.override(rootNode, value);
        });
    }

    @Override
    public int unresolvedPlaceholderCount() {
        return unresolvedPlaceholders;
    }

    @Override
    public YamlConfiguration resolvePlaceholders() {
        int stillUnresolved = 0;

        JsonNode newRootNode = null;

        return new YamlConfiguration(newRootNode, stillUnresolved);
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

    @Override
    public <X> X as(Class<X> type) throws ConversionException {
        return parseNodeToClass(rootNode, type);
    }

    private Optional<JsonNode> nodeAt(String property) {
        NodePath nodePath = new NodePath(property);

        return nodePath.findMatchingDescendant(rootNode);
    }

    private static <T> T parseNodeToClass(JsonNode node, Class<T> tClass) {
        JsonParser parser = node.traverse();

        try {
            return YAML_MAPPER.readValue(parser, tClass);
        } catch (IOException e) {
            throw propagate(e);
        }
    }
}
