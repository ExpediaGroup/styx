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
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.StaticPipelineFactory;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;

import java.util.List;
import java.util.Map;
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
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;
    private final NettyExecutor executor;


    public StyxPipelineFactory(
            RoutingObjectFactory.Context builtinRoutingObjects,
            Environment environment,
            Map<String, StyxService> services,
            List<NamedPlugin> plugins,
            NettyExecutor executor) {
        this.builtinRoutingObjects = requireNonNull(builtinRoutingObjects);
        this.environment = requireNonNull(environment);
        this.services = requireNonNull(services);
        this.plugins = requireNonNull(plugins);
        this.executor = requireNonNull(executor);
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

        if (rootHandlerNode.isPresent()) {
            return Builtins.build(ImmutableList.of("httpPipeline"), routingObjectFactoryContext, toRoutingConfigNode(rootHandlerNode.get()));
        }

        Registry<BackendService> registry = (Registry<BackendService>) services.get("backendServiceRegistry");
        return new StaticPipelineFactory(
                environment,
                registry != null ? registry : new MemoryBackedRegistry<>(),
                plugins,
                executor,
                requestTracking)
                .build();
    }
}
