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

import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.routing.config.BuiltinHandlersFactory;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RoutingConfigDefinition;
import rx.Observable;

import java.util.List;

import static com.hotels.styx.routing.config.RoutingSupport.append;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;

/**
 * Routing object that proxies a request to a configured backend.
 */
public class ProxyToBackend implements HttpHandler2 {
    private final HttpClient client;

    private ProxyToBackend(HttpClient client) {
        this.client = client;
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return client.sendRequest(request);
    }

    /**
     * ProxyToBackend factory that instantiates an object from the Yaml configuration.
     */
    public static class ConfigFactory implements HttpHandlerFactory {
        private final BackendServiceClientFactory clientFactory;

        public ConfigFactory(BackendServiceClientFactory clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        public HttpHandler2 build(List<String> parents, BuiltinHandlersFactory builder, RoutingConfigDefinition configBlock) {
            JsonNodeConfig jsConfig = new JsonNodeConfig(configBlock.config());

            BackendService backendService = jsConfig
                    .get("backend", BackendService.class)
                    .orElseThrow(() ->  missingAttributeError(configBlock, join(".", parents), "backend"));

            JsonNode origins = jsConfig
                    .get("backend.origins", JsonNode.class)
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", append(parents, "backend")), "origins"));

            return new ProxyToBackend(clientFactory.createClient(backendService));
        }
    }
}
