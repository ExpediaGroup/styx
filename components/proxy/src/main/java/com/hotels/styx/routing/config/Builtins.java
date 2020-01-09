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
package com.hotels.styx.routing.config;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.InetServer;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HostProxy;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.LoadBalancingGroup;
import com.hotels.styx.routing.handlers.PathPrefixRouter;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.RouteRefLookup;
import com.hotels.styx.routing.handlers.StaticResponseHandler;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;
import com.hotels.styx.serviceproviders.ServiceProviderFactory;
import com.hotels.styx.serviceproviders.StyxServerFactory;
import com.hotels.styx.services.HealthCheckMonitoringService;
import com.hotels.styx.services.HealthCheckMonitoringServiceFactory;
import com.hotels.styx.services.YamlFileConfigurationService;
import com.hotels.styx.services.YamlFileConfigurationServiceFactory;

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
    public static final String STATIC_RESPONSE = "StaticResponseHandler";
    public static final String CONDITION_ROUTER = "ConditionRouter";
    public static final String INTERCEPTOR_PIPELINE = "InterceptorPipeline";
    public static final String PROXY_TO_BACKEND = "ProxyToBackend";
    public static final String PATH_PREFIX_ROUTER = "PathPrefixRouter";
    public static final String HOST_PROXY = "HostProxy";
    public static final String LOAD_BALANCING_GROUP = "LoadBalancingGroup";

    public static final String HEALTH_CHECK_MONITOR = "HealthCheckMonitor";
    public static final String YAML_FILE_CONFIGURATION_SERVICE = "YamlFileConfigurationService";

    public static final String REWRITE = "Rewrite";

    public static final ImmutableMap<String, Schema.FieldType> BUILTIN_HANDLER_SCHEMAS;
    public static final ImmutableMap<String, RoutingObjectFactory> BUILTIN_HANDLER_FACTORIES;

    public static final ImmutableMap<String, HttpInterceptorFactory> INTERCEPTOR_FACTORIES =
            ImmutableMap.of(REWRITE, new RewriteInterceptor.Factory());

    public static final ImmutableMap<String, Schema.FieldType> INTERCEPTOR_SCHEMAS =
            ImmutableMap.of(REWRITE, RewriteInterceptor.SCHEMA);

    public static final ImmutableMap<String, ServiceProviderFactory> BUILTIN_SERVICE_PROVIDER_FACTORIES =
            ImmutableMap.of(HEALTH_CHECK_MONITOR, new HealthCheckMonitoringServiceFactory(),
                    YAML_FILE_CONFIGURATION_SERVICE, new YamlFileConfigurationServiceFactory()
            );

    public static final ImmutableMap<String, Schema.FieldType> BUILTIN_SERVICE_PROVIDER_SCHEMAS =
            ImmutableMap.of(HEALTH_CHECK_MONITOR, HealthCheckMonitoringService.SCHEMA,
                    YAML_FILE_CONFIGURATION_SERVICE, YamlFileConfigurationService.SCHEMA);

    public static final ImmutableMap<String, StyxServerFactory> BUILTIN_SERVER_FACTORIES = ImmutableMap.of();
    public static final ImmutableMap<String, Schema.FieldType> BUILTIN_SERVER_SCHEMAS = ImmutableMap.of();

    public static final RouteRefLookup DEFAULT_REFERENCE_LOOKUP = reference -> (request, ctx) ->
            Eventual.of(response(NOT_FOUND)
                    .body(format("Handler not found for '%s'.", reference), UTF_8)
                    .build()
                    .stream());


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

    /**
     * Buiulds a routing object.
     *
     * @param parents fully qualified attribute name
     * @param context a context to styx environment
     * @param configNode routing object configuration
     *
     * @return a routing object
     */
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

    /**
     * Builds a HTTP interceptor.
     *
     * @param configBlock configuration
     * @param interceptorFactories built-in interceptor factories by name
     *
     * @return an HTTP interceptor
     */
    public static HttpInterceptor build(StyxObjectConfiguration configBlock, Map<String, HttpInterceptorFactory> interceptorFactories) {
        if (configBlock instanceof StyxObjectDefinition) {
            StyxObjectDefinition block = (StyxObjectDefinition) configBlock;
            String type = block.type();

            HttpInterceptorFactory constructor = interceptorFactories.get(type);
            checkArgument(constructor != null, format("Unknown service provider type '%s'", type));

            return constructor.build(block);
        } else {
            throw new UnsupportedOperationException("Routing config node must be an config block, not a reference");
        }
    }

    /**
     * Builds a Styx service.
     *
     * @param name Styx service name
     * @param providerDef Styx service object configuration
     * @param factories Service provider factories by name
     * @param context Routing object factory context
     *
     * @return a Styx service
     */
    public static StyxService build(
            String name,
            StyxObjectDefinition providerDef,
            StyxObjectStore<StyxObjectRecord<StyxService>> serviceDb,
            Map<String, ServiceProviderFactory> factories,
            RoutingObjectFactory.Context context) {
        ServiceProviderFactory constructor = factories.get(providerDef.type());
        checkArgument(constructor != null, format("Unknown service provider type '%s' for '%s' provider", providerDef.type(), providerDef.name()));

        return constructor.create(name, context, providerDef.config(), serviceDb);
    }

    /**
     * Builds a Styx server.
     *
     * Styx server is a service that can accept incoming traffic from the client hosts.
     *
     * @param name Styx service name
     * @param serverDef Styx service object configuration
     * @param factories Service provider factories by name
     * @param context Routing object factory context
     *
     * @return a Styx service
     */
    public static InetServer buildServer(
            String name,
            StyxObjectDefinition serverDef,
            StyxObjectStore<StyxObjectRecord<InetServer>> serverDb,
            Map<String, StyxServerFactory> factories,
            RoutingObjectFactory.Context context) {
        StyxServerFactory constructor = factories.get(serverDef.type());
        checkArgument(constructor != null, format("Unknown server type '%s' for '%s' provider", serverDef.type(), serverDef.name()));

        return constructor.create(name, context, serverDef.config(), serverDb);
    }
}
