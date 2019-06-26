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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HostProxy;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.LoadBalancingGroup;
import com.hotels.styx.routing.handlers.PathPrefixRouter;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.RouteRefLookup;
import com.hotels.styx.routing.handlers.StaticResponseHandler;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Contains mappings of builtin routing object and interceptor names to their factory methods.
 */
public final class Builtins {
    public static final ImmutableMap<String, Schema.FieldType> BUILTIN_HANDLER_SCHEMAS;
    public static final ImmutableMap<String, RoutingObjectFactory> BUILTIN_HANDLER_FACTORIES;
    public static final RouteRefLookup DEFAULT_REFERENCE_LOOKUP = reference -> (request, ctx) ->
            Eventual.of(response(NOT_FOUND)
                    .body(format("Handler not found for '%s'.", reference), UTF_8)
                    .build()
                    .stream());
    public static final ImmutableMap<String, HttpInterceptorFactory> INTERCEPTOR_FACTORIES =
            ImmutableMap.of("Rewrite", new RewriteInterceptor.Factory());

    private static final String STATIC_RESPONSE = "StaticResponseHandler";
    private static final String CONDITION_ROUTER = "ConditionRouter";
    private static final String INTERCEPTOR_PIPELINE = "InterceptorPipeline";
    private static final String PROXY_TO_BACKEND = "ProxyToBackend";
    private static final String PATH_PREFIX_ROUTER = "PathPrefixRouter";
    private static final String HOST_PROXY = "HostProxy";
    private static final String LOAD_BALANCING_GROUP = "LoadBalancingGroup";


    static {
        BUILTIN_HANDLER_FACTORIES = ImmutableMap.<String, RoutingObjectFactory>builder()
                .put(STATIC_RESPONSE, new StaticResponseHandler.Factory())
                .put(CONDITION_ROUTER, new ConditionRouter.Factory())
                .put(INTERCEPTOR_PIPELINE, new HttpInterceptorPipeline.Factory())
                .put(PROXY_TO_BACKEND, new ProxyToBackend.Factory())
                .put(PATH_PREFIX_ROUTER, new PathPrefixRouter.Factory())
                .put(HOST_PROXY, new HostProxy.Factory())
                .put(LOAD_BALANCING_GROUP, new LoadBalancingGroup.Factory())
                .build();

        BUILTIN_HANDLER_SCHEMAS = ImmutableMap.<String, Schema.FieldType>builder()
                .put(STATIC_RESPONSE, StaticResponseHandler.SCHEMA)
                .put(CONDITION_ROUTER, ConditionRouter.SCHEMA)
                .put(INTERCEPTOR_PIPELINE, HttpInterceptorPipeline.SCHEMA)
                .put(PROXY_TO_BACKEND, ProxyToBackend.SCHEMA)
                .put(PATH_PREFIX_ROUTER, PathPrefixRouter.SCHEMA)
                .put(HOST_PROXY, HostProxy.SCHEMA)
                .put(LOAD_BALANCING_GROUP,  LoadBalancingGroup.Companion.getSCHEMA())
                .build();
    }

    private Builtins() {
    }

    public static RoutingObject build(List<String> parents, RoutingObjectFactory.Context context, StyxObjectConfiguration configNode) {
        if (configNode instanceof StyxObjectDefinition) {
            StyxObjectDefinition configBlock = (StyxObjectDefinition) configNode;
            String type = configBlock.type();

            RoutingObjectFactory factory = context.objectFactories().get(type);
            checkArgument(factory != null, format("Unknown handler type '%s'", type));

            return factory.build(parents, context, configBlock);
        } else if (configNode instanceof StyxObjectReference) {
            return (request, httpContext) -> context.refLookup()
                    .apply((StyxObjectReference) configNode)
                    .handle(request, httpContext);
        } else {
            throw new UnsupportedOperationException(format("Unsupported configuration node type: '%s'", configNode.getClass().getName()));
        }
    }

    public static HttpInterceptor build(StyxObjectConfiguration configBlock, Map<String, HttpInterceptorFactory> interceptorFactories) {
        if (configBlock instanceof StyxObjectDefinition) {
            StyxObjectDefinition block = (StyxObjectDefinition) configBlock;
            String type = block.type();

            HttpInterceptorFactory constructor = interceptorFactories.get(type);
            checkArgument(constructor != null, format("Unknown handler type '%s'", type));

            return constructor.build(block);
        } else {
            throw new UnsupportedOperationException("Routing config node must be an config block, not a reference");
        }
    }
}
