/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.routing.handlers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.BackendServicesRouter;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.routing.config.BuiltinHandlersFactory;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RoutingConfigDefinition;
import rx.Observable;

import java.util.List;
import java.util.Map;

import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.format;
import static java.lang.String.join;

/**
 * A HTTP handler that proxies requests to backend services based on the path prefix.
 */
public class BackendServiceProxy implements HttpHandler2 {

    private final RouteHandlerAdapter handler;

    private BackendServiceProxy(BackendServiceClientFactory serviceClientFactory, Registry<BackendService> registry) {
        BackendServicesRouter router = new BackendServicesRouter(serviceClientFactory);
        registry.addListener(router);
        handler = new RouteHandlerAdapter(router);
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return handler.handle(request, context);
    }

    /**
     * Builds a BackendServiceProxy from yaml routing configuration.
     */
    public static class ConfigFactory implements HttpHandlerFactory {
        private final BackendServiceClientFactory serviceClientFactory;
        private final Map<String, Service> services;

        private static StyxBackendServiceClientFactory serviceClientFactory(Environment environment) {
            ProxyServerConfig proxyConfig = environment.styxConfig().proxyServerConfig();
            return new StyxBackendServiceClientFactory(environment, proxyConfig.clientWorkerThreadsCount());
        }

        @VisibleForTesting
        ConfigFactory(BackendServiceClientFactory serviceClientFactory, Map<String, Service> services) {
            this.serviceClientFactory = serviceClientFactory;
            this.services = services;
        }

        public ConfigFactory(Environment environment, Map<String, Service> services) {
            this.services = services;
            this.serviceClientFactory = serviceClientFactory(environment);
        }

        @Override
        public HttpHandler2 build(List<String> parents, BuiltinHandlersFactory x, RoutingConfigDefinition configBlock) {
            JsonNodeConfig config = new JsonNodeConfig(configBlock.config());
            String provider = config.get("backendProvider")
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", parents), "backendProvider"));

            Service service = services.get(provider);
            if (service == null) {
                throw new IllegalArgumentException(
                        format("No such backend service provider exists, attribute='%s', name='%s'",
                                join(".", append(parents, "backendProvider")), provider));
            }
            if (!(service instanceof Registry)) {
                throw new IllegalArgumentException(
                        format("Attribute '%s' of BackendServiceProxy must refer to a BackendServiceRegistry service, name='%s'.",
                                join(".", append(parents, "backendProvider")), provider));
            }
            Registry<BackendService> registry = (Registry<BackendService>) service;

            return new BackendServiceProxy(serviceClientFactory, registry);
        }
    }

}
