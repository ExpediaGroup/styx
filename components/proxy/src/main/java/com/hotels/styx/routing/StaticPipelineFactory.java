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
package com.hotels.styx.routing;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.Environment;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.BackendServicesRouter;
import com.hotels.styx.proxy.InterceptorPipelineBuilder;
import com.hotels.styx.proxy.RouteHandlerAdapter;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;

import static java.util.Objects.requireNonNull;

/**
 * Builds a static "backwards compatibility" pipeline which is just a sequence of plugins
 * followed by backend service proxy.
 */
public class StaticPipelineFactory {
    private final BackendServiceClientFactory clientFactory;
    private final Environment environment;
    private final Registry<BackendService> registry;
    private final Iterable<NamedPlugin> plugins;
    private final NettyExecutor executor;
    private final boolean trackRequests;

    @VisibleForTesting
    StaticPipelineFactory(BackendServiceClientFactory clientFactory,
                          Environment environment,
                          Registry<BackendService> registry,
                          Iterable<NamedPlugin> plugins,
                          NettyExecutor executor,
                          boolean trackRequests) {
        this.clientFactory = requireNonNull(clientFactory);
        this.environment = requireNonNull(environment);
        this.registry = requireNonNull(registry);
        this.plugins = requireNonNull(plugins);
        this.executor = requireNonNull(executor);
        this.trackRequests = trackRequests;
    }

    public StaticPipelineFactory(Environment environment,
                                 Registry<BackendService> registry,
                                 Iterable<NamedPlugin> plugins,
                                 NettyExecutor executor,
                                 boolean trackRequests) {
        this(createClientFactory(environment), environment, registry, plugins, executor, trackRequests);
    }

    private static BackendServiceClientFactory createClientFactory(Environment environment) {
        return new StyxBackendServiceClientFactory(environment);
    }

    public RoutingObject build() {
        BackendServicesRouter backendServicesRouter = new BackendServicesRouter(clientFactory, environment, executor);
        registry.addListener(backendServicesRouter);
        RouteHandlerAdapter router = new RouteHandlerAdapter(backendServicesRouter);

        return new InterceptorPipelineBuilder(environment, plugins, router, trackRequests).build();
    }
}
