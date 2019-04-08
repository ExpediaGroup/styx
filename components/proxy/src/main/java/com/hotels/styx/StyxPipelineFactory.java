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
package com.hotels.styx;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.configuration.RouteDatabase;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.HttpPipelineFactory;
import com.hotels.styx.routing.StaticPipelineFactory;
import com.hotels.styx.routing.config.RoutingObjectConfig;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.startup.PipelineFactory;
import com.hotels.styx.startup.StyxServerComponents;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.BuiltInInterceptors.internalStyxInterceptors;
import static com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode;

/**
 * Produces the pipeline for the Styx proxy server.
 */
public final class StyxPipelineFactory implements PipelineFactory {

    private final RouteDatabase routeDb;
    private final RoutingObjectFactory routingObjectFactory;
    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;

    public StyxPipelineFactory(
            RouteDatabase routeDb,
            RoutingObjectFactory routingObjectFactory,
            Environment environment,
            Map<String, StyxService> services,
            List<NamedPlugin> plugins) {
        this.routeDb = routeDb;
        this.routingObjectFactory = routingObjectFactory;
        this.environment = environment;
        this.services = services;
        this.plugins = plugins;
    }

    @Override
    public HttpHandler create(StyxServerComponents config) {
        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        List<HttpInterceptor> internalInterceptors = internalStyxInterceptors(environment.styxConfig());

        return new HttpInterceptorPipeline(
                internalInterceptors,
                configuredPipeline(routingObjectFactory),
                requestTracking);
    }

    private HttpHandler configuredPipeline(RoutingObjectFactory routingObjectFactory) {
        HttpPipelineFactory pipelineBuilder;

        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        Optional<JsonNode> rootHandlerNode = environment.configuration().get("httpPipeline", JsonNode.class);

        pipelineBuilder = rootHandlerNode
                .map(jsonNode -> {
                    RoutingObjectConfig node = toRoutingConfigNode(jsonNode);
                    return (HttpPipelineFactory) () -> routingObjectFactory.build(ImmutableList.of("httpPipeline"), routeDb, node);
                })
                .orElseGet(() -> {
                    Registry<BackendService> backendServicesRegistry = (Registry<BackendService>) services.get("backendServiceRegistry");
                    return new StaticPipelineFactory(environment, backendServicesRegistry, plugins, requestTracking);
                });

        return pipelineBuilder.build();
    }
}
