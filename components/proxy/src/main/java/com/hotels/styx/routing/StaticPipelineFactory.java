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
package com.hotels.styx.routing;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.BackendServicesRouter;
import com.hotels.styx.proxy.InterceptorPipelineBuilder;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;

import java.util.function.Supplier;

/**
 * Builds a static "backwards compatibility" pipeline which is just a sequence of plugins
 * followed by backend service proxy.
 */
public class StaticPipelineFactory implements HttpPipelineFactory {
    private final BackendServiceClientFactory clientFactory;
    private final Environment environment;
    private final Registry<BackendService> registry;
    private final Supplier<Iterable<NamedPlugin>> pluginsSupplier;

    @VisibleForTesting
    StaticPipelineFactory(BackendServiceClientFactory clientFactory, Environment environment, Registry<BackendService> registry, Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
        this.clientFactory = clientFactory;
        this.environment = environment;
        this.registry = registry;
        this.pluginsSupplier = pluginsSupplier;
    }

    public StaticPipelineFactory(Environment environment, Registry<BackendService> registry, Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
        this(createClientFactory(environment), environment, registry, pluginsSupplier);
    }

    private static BackendServiceClientFactory createClientFactory(Environment environment) {
        return new StyxBackendServiceClientFactory(environment);
    }

    @Override
    public HttpHandler2 build() {
        BackendServicesRouter backendServicesRouter = new BackendServicesRouter(clientFactory, environment);
        registry.addListener(backendServicesRouter);
        RouteHandlerAdapter router = new RouteHandlerAdapter(backendServicesRouter);

        return new InterceptorPipelineBuilder(environment, pluginsSupplier, router).build();
    }
}
