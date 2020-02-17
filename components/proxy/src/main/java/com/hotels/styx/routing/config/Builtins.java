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
import com.hotels.styx.routing.handlers.RouteRefLookup;
import com.hotels.styx.routing.handlers.StaticResponseHandler;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;
import com.hotels.styx.servers.StyxHttpServer;
import com.hotels.styx.servers.StyxHttpServerFactory;
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

    public static class StyxObjectDescriptor<T> {
        private final String typeName;
        private final T factory;
        private final Schema.FieldType schema;

        public StyxObjectDescriptor(String typeName, T factory, Schema.FieldType schema) {
            this.typeName = typeName;
            this.factory = factory;
            this.schema = schema;
        }

        public String type() {
            return typeName;
        }

        public T factory() {
            return factory;
        }

        public Schema.FieldType schema() {
            return schema;
        }

    }

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

    public static final ImmutableMap<String, StyxObjectDescriptor<RoutingObjectFactory>> ROUTING_OBJECT_DESCRIPTORS;
    public static final ImmutableMap<String, StyxObjectDescriptor<ServiceProviderFactory>> SERVICE_PROVIDER_DESCRIPTORS;
    public static final ImmutableMap<String, StyxObjectDescriptor<StyxServerFactory>> SERVER_DESCRIPTORS;

    public static final ImmutableMap<String, HttpInterceptorFactory> INTERCEPTOR_FACTORIES =
            ImmutableMap.of(REWRITE, new RewriteInterceptor.Factory());

    public static final ImmutableMap<String, Schema.FieldType> INTERCEPTOR_SCHEMAS =
            ImmutableMap.of(REWRITE, RewriteInterceptor.SCHEMA);

    public static final RouteRefLookup DEFAULT_REFERENCE_LOOKUP = reference -> (request, ctx) ->
            Eventual.of(response(NOT_FOUND)
                    .body(format("Handler not found for '%s'.", reference), UTF_8)
                    .build()
                    .stream());

    static {
        ROUTING_OBJECT_DESCRIPTORS = ImmutableMap.<String, StyxObjectDescriptor<RoutingObjectFactory>>builder()
                .put(STATIC_RESPONSE, new StyxObjectDescriptor<>(     STATIC_RESPONSE,      new StaticResponseHandler.Factory(),   StaticResponseHandler.SCHEMA))
                .put(CONDITION_ROUTER, new StyxObjectDescriptor<>(    CONDITION_ROUTER,     new ConditionRouter.Factory(),         ConditionRouter.SCHEMA))
                .put(INTERCEPTOR_PIPELINE, new StyxObjectDescriptor<>(INTERCEPTOR_PIPELINE, new HttpInterceptorPipeline.Factory(), HttpInterceptorPipeline.SCHEMA))
                .put(PATH_PREFIX_ROUTER, new StyxObjectDescriptor<>(  PATH_PREFIX_ROUTER,   new PathPrefixRouter.Factory(),        PathPrefixRouter.SCHEMA))
                .put(HOST_PROXY, new StyxObjectDescriptor<>(          HOST_PROXY,           new HostProxy.Factory(),               HostProxy.SCHEMA))
                .put(LOAD_BALANCING_GROUP, new StyxObjectDescriptor<>(LOAD_BALANCING_GROUP, new LoadBalancingGroup.Factory(),      LoadBalancingGroup.Companion.getSCHEMA()))
                .build();

        SERVICE_PROVIDER_DESCRIPTORS = ImmutableMap.<String, StyxObjectDescriptor<ServiceProviderFactory>>builder()
                .put(HEALTH_CHECK_MONITOR,            new StyxObjectDescriptor<>(HEALTH_CHECK_MONITOR,            new HealthCheckMonitoringServiceFactory(), HealthCheckMonitoringService.SCHEMA))
                .put(YAML_FILE_CONFIGURATION_SERVICE, new StyxObjectDescriptor<>(YAML_FILE_CONFIGURATION_SERVICE, new YamlFileConfigurationServiceFactory(), YamlFileConfigurationService.SCHEMA))
                .build();

        SERVER_DESCRIPTORS = ImmutableMap.<String, StyxObjectDescriptor<StyxServerFactory>>builder()
                .put("HttpServer",            new StyxObjectDescriptor<>("HttpServer", new StyxHttpServerFactory(), StyxHttpServer.SCHEMA))
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
