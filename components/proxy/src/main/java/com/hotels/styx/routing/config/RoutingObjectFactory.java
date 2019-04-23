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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.Environment;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.StaticResponseHandler;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Builds a routing object based on its actual type.
 */
public class RoutingObjectFactory {
    private static final Map<String, HttpHandlerFactory> BUILTIN_HANDLERS = ImmutableMap.<String, HttpHandlerFactory>builder()
            .put("StaticResponseHandler", new StaticResponseHandler.Factory())
            .put("ConditionRouter", new ConditionRouter.Factory())
            .put("InterceptorPipeline", new HttpInterceptorPipeline.Factory())
            .put("ProxyToBackend", new ProxyToBackend.Factory())
            .build();

    private final Environment environment;
    private StyxObjectStore<RoutingObjectRecord> routeObjectStore;
    private final Iterable<NamedPlugin> plugins;
    private final BuiltinInterceptorsFactory interceptorFactory;
    private final Map<String, HttpHandlerFactory> builtInObjectTypes;
    private final boolean requestTracking;

    @VisibleForTesting
    public RoutingObjectFactory(
            Map<String, HttpHandlerFactory> builtInObjectTypes,
            Environment environment,
            StyxObjectStore<RoutingObjectRecord> routeObjectStore, Iterable<NamedPlugin> plugins,
            BuiltinInterceptorsFactory interceptorFactory,
            boolean requestTracking) {
        this.builtInObjectTypes = requireNonNull(builtInObjectTypes);
        this.environment = requireNonNull(environment);
        this.routeObjectStore = requireNonNull(routeObjectStore);
        this.plugins = requireNonNull(plugins);
        this.interceptorFactory = requireNonNull(interceptorFactory);
        this.requestTracking = requestTracking;
    }

    public RoutingObjectFactory(
            Environment environment,
            StyxObjectStore<RoutingObjectRecord> routeObjectStore,
            List<NamedPlugin> plugins,
            BuiltinInterceptorsFactory interceptorFactory,
            boolean requestTracking) {
        this(BUILTIN_HANDLERS, environment, routeObjectStore, plugins, interceptorFactory, requestTracking);
    }

    public HttpHandler build(List<String> parents, RoutingObjectConfiguration configNode) {
        if (configNode instanceof RoutingObjectDefinition) {
            RoutingObjectDefinition configBlock = (RoutingObjectDefinition) configNode;
            String type = configBlock.type();

            HttpHandlerFactory factory = builtInObjectTypes.get(type);
            checkArgument(factory != null, format("Unknown handler type '%s'", type));

            HttpHandlerFactory.Context context = new HttpHandlerFactory.Context(
                    environment,
                    routeObjectStore,
                    this,
                    plugins,
                    interceptorFactory,
                    requestTracking);

            return factory.build(parents, context, configBlock);
        } else if (configNode instanceof RoutingObjectReference) {
            RoutingObjectReference reference = (RoutingObjectReference) configNode;

            return (request, context) -> routeObjectStore.get(reference.name())
                    .map(handler -> handler.getHandler().handle(request, context))
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
