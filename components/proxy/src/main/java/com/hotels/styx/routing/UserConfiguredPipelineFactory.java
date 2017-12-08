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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.service.spi.StyxService;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.config.BuiltinHandlersFactory;
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory;
import com.hotels.styx.routing.config.RoutingConfigDefinition;
import com.hotels.styx.routing.handlers.BackendServiceProxy;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.StaticResponseHandler;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;

import java.util.Map;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Builds an user-configured HTTP pipeline.
 */
public class UserConfiguredPipelineFactory implements HttpPipelineFactory {
    private final Environment environment;
    private final Configuration configuration;
    private final Supplier<Iterable<NamedPlugin>> pluginsSupplier;
    private final Map<String, StyxService> registries;

    public UserConfiguredPipelineFactory(Environment environment, Configuration configuration, Supplier<Iterable<NamedPlugin>> pluginsSupplier, Map<String, StyxService> services) {
        this.environment = environment;
        this.configuration = checkNotNull(configuration);
        this.pluginsSupplier = checkNotNull(pluginsSupplier);
        this.registries = checkNotNull(services);
    }

    private static StyxBackendServiceClientFactory serviceClientFactory(Environment environment) {
        ProxyServerConfig proxyConfig = environment.styxConfig().proxyServerConfig();
        return new StyxBackendServiceClientFactory(environment, proxyConfig.clientWorkerThreadsCount());
    }

    @Override
    public HttpHandler2 build() {
        BuiltinInterceptorsFactory builtinInterceptorsFactory = new BuiltinInterceptorsFactory(ImmutableMap.of(
            "Rewrite", new RewriteInterceptor.ConfigFactory()
        ));

        BuiltinHandlersFactory builtinHandlersFactory = new BuiltinHandlersFactory(
                ImmutableMap.of(
                        "StaticResponseHandler", new StaticResponseHandler.ConfigFactory(),
                        "ConditionRouter", new ConditionRouter.ConfigFactory(),
                        "BackendServiceProxy", new BackendServiceProxy.ConfigFactory(environment, registries),
                        "InterceptorPipeline", new HttpInterceptorPipeline.ConfigFactory(pluginsSupplier, builtinInterceptorsFactory),
                        "ProxyToBackend", new ProxyToBackend.ConfigFactory(serviceClientFactory(environment))
                ));

        RoutingConfigDefinition pipelineConfig = configuration.get("httpPipeline", RoutingConfigDefinition.class).get();

        return builtinHandlersFactory.build(ImmutableList.of("httpPipeline"), pipelineConfig);
    }
}
