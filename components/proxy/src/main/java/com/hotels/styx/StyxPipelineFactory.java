/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;

import java.util.Optional;

import static com.hotels.styx.BuiltInInterceptors.internalStyxInterceptors;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;
import static java.util.Objects.requireNonNull;

/**
 * Produces the pipeline for the Styx proxy server.
 */
public class StyxPipelineFactory {

    private final RoutingObjectFactory.Context builtinRoutingObjects;
    private final Environment environment;


    public StyxPipelineFactory(
            RoutingObjectFactory.Context builtinRoutingObjects,
            Environment environment) {
        this.builtinRoutingObjects = requireNonNull(builtinRoutingObjects);
        this.environment = requireNonNull(environment);
    }

    public HttpHandler create() {
        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        return new HttpInterceptorPipeline(
                internalStyxInterceptors(environment.styxConfig(), environment.httpMessageFormatter()),
                configuredPipeline(builtinRoutingObjects),
                requestTracking);
    }

    private RoutingObject configuredPipeline(RoutingObjectFactory.Context routingObjectFactoryContext) {
        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        Optional<JsonNode> rootHandlerNode = environment.configuration().get("httpPipeline", JsonNode.class);

        if (!rootHandlerNode.isPresent()) {
            throw new IllegalArgumentException("Root handler is not present");
        }

        return Builtins.build(ImmutableList.of("httpPipeline"), routingObjectFactoryContext, toRoutingConfigNode(rootHandlerNode.get()));
    }
}
