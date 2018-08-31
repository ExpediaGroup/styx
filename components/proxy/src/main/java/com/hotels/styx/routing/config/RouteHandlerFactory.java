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

import com.google.common.base.Preconditions;
import com.hotels.styx.api.HttpHandler;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Builds a routing object based on its actual type.
 */
public class RouteHandlerFactory {
    private final Map<String, HttpHandlerFactory> factories;
    private Map<String, HttpHandler> handlers;

    public RouteHandlerFactory(Map<String, HttpHandlerFactory> factories, Map<String, HttpHandler> handlers) {
        this.factories = requireNonNull(factories);
        this.handlers = requireNonNull(handlers);
    }

    public HttpHandler build(List<String> parents, RouteHandlerConfig configNode) {
        if (configNode instanceof RouteHandlerDefinition) {
            RouteHandlerDefinition configBlock = (RouteHandlerDefinition) configNode;
            String type = configBlock.type();

            HttpHandlerFactory factory = factories.get(type);
            Preconditions.checkArgument(factory != null, format("Unknown handler type '%s'", type));

            return factory.build(parents, this, configBlock);
        } else if (configNode instanceof RouteHandlerReference) {
            RouteHandlerReference reference = (RouteHandlerReference) configNode;

            HttpHandler handler = handlers.get(reference.name());
            if (handler == null) {
                throw new IllegalArgumentException(format("Non-existent handler instance: '%s'", reference.name()));
            }

            return handler;
        } else {
            throw new UnsupportedOperationException(format("Unsupported configuration node type: '%s'", configNode.getClass().getName()));
        }
    }
}
