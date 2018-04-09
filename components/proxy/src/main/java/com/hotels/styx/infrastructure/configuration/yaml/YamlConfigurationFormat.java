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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.Resource;
import com.hotels.styx.infrastructure.configuration.ConfigurationFormat;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.extractPlaceholders;
import static com.hotels.styx.infrastructure.configuration.yaml.PlaceholderResolver.replacePlaceholder;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * YAML-based configuration format.
 */
public class YamlConfigurationFormat implements ConfigurationFormat<YamlConfiguration> {
    public static final YamlConfigurationFormat YAML = new YamlConfigurationFormat();

    private static final Logger LOGGER = getLogger(YamlConfigurationFormat.class);

    static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true);

    private YamlConfigurationFormat() {
    }

    @Override
    public YamlConfiguration deserialise(String string) {
        return new YamlConfiguration(node(string));
    }

    @Override
    public YamlConfiguration deserialise(Resource resource) {
        return new YamlConfiguration(node(resource));
    }

    private static JsonNode node(String yaml) {
        try {
            return YAML_MAPPER.readTree(yaml);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private static JsonNode node(Resource resource) {
        LOGGER.info("Loading configuration from file: path={}", resource);

        try (InputStream inputStream = resource.inputStream()) {
            return YAML_MAPPER.readTree(inputStream);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    @Override
    public String resolvePlaceholdersInText(String text, Map<String, String> overrides) {
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

    @Override
    public String toString() {
        return "YAML";
    }
}
