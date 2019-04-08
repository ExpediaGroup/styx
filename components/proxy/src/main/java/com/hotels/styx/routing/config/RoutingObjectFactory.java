/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.google.common.base.Preconditions;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.RouteDatabase;

import java.util.List;
import java.util.Map;

import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponse.response;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Builds a routing object based on its actual type.
 */
public class RoutingObjectFactory {
    private final Map<String, HttpHandlerFactory> builtInObjectTypes;

    public RoutingObjectFactory(Map<String, HttpHandlerFactory> builtInObjectTypes) {
        this.builtInObjectTypes = requireNonNull(builtInObjectTypes);
    }

    public HttpHandler build(List<String> parents, RouteDatabase routeDb, RoutingObjectConfig configNode) {
        if (configNode instanceof RoutingObjectDefinition) {
            RoutingObjectDefinition configBlock = (RoutingObjectDefinition) configNode;
            String type = configBlock.type();

            HttpHandlerFactory factory = builtInObjectTypes.get(type);
            Preconditions.checkArgument(factory != null, format("Unknown handler type '%s'", type));

            return factory.build(parents, routeDb, this, configBlock);
        } else if (configNode instanceof RoutingObjectReference) {
            RoutingObjectReference reference = (RoutingObjectReference) configNode;

            return (request, context) -> routeDb.handler(reference.name())
                    .map(handler -> handler.handle(request, context))
                    .orElse(Eventual.of(
                            response(NOT_FOUND)
                                    .body("Not found: " + String.join(".", parents) + "." + reference.name(), UTF_8)
                                    .build()
                                    .stream()));
        } else {
            throw new UnsupportedOperationException(format("Unsupported configuration node type: '%s'", configNode.getClass().getName()));
        }
    }
}
