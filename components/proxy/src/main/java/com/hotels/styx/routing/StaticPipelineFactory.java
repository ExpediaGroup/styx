/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.routing;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.BackendServicesRouter;
import com.hotels.styx.proxy.InterceptorPipelineBuilder;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;

/**
 * Builds a static "backwards compatibility" pipeline which is just a sequence of plugins
 * followed by backend service proxy.
 */
public class StaticPipelineFactory implements HttpPipelineFactory {
    private final BackendServiceClientFactory clientFactory;
    private final Environment environment;
    private final Registry<BackendService> registry;
    private final Iterable<NamedPlugin> plugins;

    @VisibleForTesting
    StaticPipelineFactory(BackendServiceClientFactory clientFactory, Environment environment, Registry<BackendService> registry, Iterable<NamedPlugin> plugins) {
        this.clientFactory = clientFactory;
        this.environment = environment;
        this.registry = registry;
        this.plugins = plugins;
    }

    public StaticPipelineFactory(Environment environment, Registry<BackendService> registry, Iterable<NamedPlugin> plugins) {
        this(createClientFactory(environment), environment, registry, plugins);
    }

    private static BackendServiceClientFactory createClientFactory(Environment environment) {
        return new StyxBackendServiceClientFactory(environment);
    }

    @Override
    public HttpHandler build() {
        BackendServicesRouter backendServicesRouter = new BackendServicesRouter(clientFactory, environment);
        registry.addListener(backendServicesRouter);
        RouteHandlerAdapter router = new RouteHandlerAdapter(backendServicesRouter);

        return new InterceptorPipelineBuilder(environment, plugins, router).build();
    }
}
