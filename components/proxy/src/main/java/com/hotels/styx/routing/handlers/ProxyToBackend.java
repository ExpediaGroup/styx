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
package com.hotels.styx.routing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.BackendServiceClient;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.client.connectionpool.ExpiringConnectionFactory;
import com.hotels.styx.client.connectionpool.SimpleConnectionPoolFactory;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;

import java.util.List;

import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.opaque;
import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;

/**
 * Routing object that proxies a request to a configured backend.
 *
 * @deprecated  Will be removed in Styx 1.1 release. Use a combination of HostProxy and LoadBalancingGroup
 *              to achieve same functionality.
 */
@Deprecated
public class ProxyToBackend implements RoutingObject {
    public static final Schema.FieldType SCHEMA = object(
            field("backend", object(opaque()))
    );
    private final BackendServiceClient client;

    private ProxyToBackend(BackendServiceClient client) {
        this.client = client;
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return new Eventual<>(client.sendRequest(request, context));
    }

    /**
     * ProxyToBackend factory that instantiates an object from the Yaml configuration.
     */
    public static class Factory implements RoutingObjectFactory {
        @VisibleForTesting
        static RoutingObject build(List<String> parents, Context context, StyxObjectDefinition configBlock, BackendServiceClientFactory clientFactory) {
            JsonNodeConfig jsConfig = new JsonNodeConfig(configBlock.config());

            BackendService backendService = jsConfig
                    .get("backend", BackendService.class)
                    .orElseThrow(() ->  missingAttributeError(configBlock, join(".", parents), "backend"));

            int clientWorkerThreadsCount = context.environment().configuration().proxyServerConfig().clientWorkerThreadsCount();

            boolean requestLoggingEnabled = context.environment().configuration().get("request-logging.outbound.enabled", Boolean.class)
                    .orElse(false);

            boolean longFormat = context.environment().configuration().get("request-logging.outbound.longFormat", Boolean.class)
                    .orElse(false);

            JsonNode origins = jsConfig
                    .get("backend.origins", JsonNode.class)
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", append(parents, "backend")), "origins"));

            OriginStatsFactory originStatsFactory = new CachingOriginStatsFactory(context.environment().metricRegistry());

            Connection.Factory connectionFactory = new NettyConnectionFactory.Builder()
                    .executor(NettyExecutor.create("Styx", clientWorkerThreadsCount))
                    .httpRequestOperationFactory(
                            httpRequestOperationFactoryBuilder()
                                    .flowControlEnabled(true)
                                    .originStatsFactory(originStatsFactory)
                                    .requestLoggingEnabled(requestLoggingEnabled)
                                    .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                    .longFormat(longFormat)
                                    .build())
                    .tlsSettings(backendService.tlsSettings().orElse(null))
                    .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(backendService.maxHeaderSize()).build())
                    .build();

            ConnectionPoolSettings poolSettings = backendService.connectionPoolConfig();

            if (poolSettings.connectionExpirationSeconds() > 0) {
                connectionFactory = new ExpiringConnectionFactory(poolSettings.connectionExpirationSeconds(), connectionFactory);
            }

            ConnectionPool.Factory connectionPoolFactory = new SimpleConnectionPoolFactory.Builder()
                    .connectionFactory(connectionFactory)
                    .connectionPoolSettings(poolSettings)
                    .metricRegistry(context.environment().metricRegistry())
                    .build();

            OriginsInventory inventory = new OriginsInventory.Builder(backendService.id())
                    .eventBus(context.environment().eventBus())
                    .metricsRegistry(context.environment().metricRegistry())
                    .connectionPoolFactory(connectionPoolFactory)
                    .initialOrigins(backendService.origins())
                    .build();
            return new ProxyToBackend(clientFactory.createClient(backendService, inventory, originStatsFactory));
        }

        @Override
        public RoutingObject build(List<String> fullName, Context context, StyxObjectDefinition configBlock) {
            return build(fullName, context, configBlock, new StyxBackendServiceClientFactory(context.environment()));
        }

    }
}
