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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Parses routing config objects from Yaml file.
 */
public class RoutingConfigParser {

    public static RouteHandlerConfig toRoutingConfigNode(JsonNode jsonNode) {
        if (jsonNode.getNodeType() == JsonNodeType.STRING) {
            return new RouteHandlerReference(jsonNode.asText());
        } else if (jsonNode.getNodeType() == JsonNodeType.OBJECT) {
            String name = getOrElse(jsonNode, "name", "");
            String type = getMandatory(jsonNode, "type", format("Routing config definition must have a 'type' attribute in def='%s'", name));
            JsonNode conf = jsonNode.get("config");
            return new RouteHandlerDefinition(name, type, conf);
        }
        throw new IllegalArgumentException("invalid configuration");
    }

    private static String getMandatory(JsonNode jsonNode, String attributeName, String message) {
        if (!jsonNode.has(attributeName)) {
            throw new IllegalArgumentException(message);
        }
        return requireNonNull(jsonNode.get(attributeName).asText());
    }

    private static String getOrElse(JsonNode jsonNode, String attributeName, String defaultValue) {
        return ofNullable(jsonNode.get(attributeName))
                .map(JsonNode::asText)
                .orElse(defaultValue);
    }

}
