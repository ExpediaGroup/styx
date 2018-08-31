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
package com.hotels.styx.startup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.interceptors.ConfigurationContextResolverInterceptor;
import com.hotels.styx.proxy.interceptors.HopByHopHeadersRemovingInterceptor;
import com.hotels.styx.proxy.interceptors.HttpMessageLoggingInterceptor;
import com.hotels.styx.proxy.interceptors.RequestEnrichingInterceptor;
import com.hotels.styx.proxy.interceptors.UnexpectedRequestContentLengthRemover;
import com.hotels.styx.proxy.interceptors.ViaHeaderAppendingInterceptor;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.HttpPipelineFactory;
import com.hotels.styx.routing.StaticPipelineFactory;
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RouteHandlerDefinition;
import com.hotels.styx.routing.config.RouteHandlerFactory;
import com.hotels.styx.routing.handlers.BackendServiceProxy;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.StaticResponseHandler;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hotels.styx.api.configuration.ConfigurationContextResolver.EMPTY_CONFIGURATION_CONTEXT_RESOLVER;
import static java.util.stream.Collectors.toMap;

/**
 * Produces the pipeline for the Styx proxy server.
 */
public final class StyxPipelineFactory implements PipelineFactory {
    public StyxPipelineFactory() {
    }

    @Override
    public HttpHandler create(StyxServerComponents config) {
        BuiltinInterceptorsFactory builtinInterceptorsFactory = new BuiltinInterceptorsFactory(
                ImmutableMap.of("Rewrite", new RewriteInterceptor.ConfigFactory()));

        Map<String, HttpHandlerFactory> objectFactories = createBuiltinRoutingObjectFactories(
                config.environment(),
                config.services(),
                config.plugins(),
                builtinInterceptorsFactory);

        RouteHandlerFactory routeHandlerFactory = new RouteHandlerFactory(objectFactories, new ConcurrentHashMap<>());

        return styxHttpPipeline(
                config.environment().styxConfig(),
                configuredPipeline(config.environment(), config.services(), config.plugins(), routeHandlerFactory));
    }

    private static HttpHandler styxHttpPipeline(StyxConfig config, HttpHandler interceptorsPipeline) {
        ImmutableList.Builder<HttpInterceptor> builder = ImmutableList.builder();

        boolean loggingEnabled = config.get("request-logging.inbound.enabled", Boolean.class)
                .map(isEnabled -> isEnabled || config.get("request-logging.enabled", Boolean.class).orElse(false))
                .orElse(false);

        boolean longFormatEnabled = config.get("request-logging.inbound.longFormat", Boolean.class)
                .orElse(false);

        if (loggingEnabled) {
            builder.add(new HttpMessageLoggingInterceptor(longFormatEnabled));
        }

        builder.add(new ConfigurationContextResolverInterceptor(EMPTY_CONFIGURATION_CONTEXT_RESOLVER));
        builder.add(new UnexpectedRequestContentLengthRemover());
        builder.add(new ViaHeaderAppendingInterceptor());
        builder.add(new HopByHopHeadersRemovingInterceptor());
        builder.add(new RequestEnrichingInterceptor(config.styxHeaderConfig()));

        return new HttpInterceptorPipeline(builder.build(), interceptorsPipeline);
    }

    private static HttpHandler configuredPipeline(
            Environment environment,
            Map<String, StyxService> servicesFromConfig,
            Iterable<NamedPlugin> plugins,
            RouteHandlerFactory routeHandlerFactory
    ) {
        HttpPipelineFactory pipelineBuilder;

        if (environment.configuration().get("httpPipeline", RouteHandlerDefinition.class).isPresent()) {
            pipelineBuilder = () -> {
                RouteHandlerDefinition pipelineConfig = environment.configuration().get("httpPipeline", RouteHandlerDefinition.class).get();
                return routeHandlerFactory.build(ImmutableList.of("httpPipeline"), pipelineConfig);
            };
        } else {
            Registry<BackendService> backendServicesRegistry = (Registry<BackendService>) servicesFromConfig.get("backendServiceRegistry");
            pipelineBuilder = new StaticPipelineFactory(environment, backendServicesRegistry, plugins);
        }

        return pipelineBuilder.build();
    }

    private static ImmutableMap<String, HttpHandlerFactory> createBuiltinRoutingObjectFactories(
            Environment environment,
            Map<String, StyxService> servicesFromConfig,
            Iterable<NamedPlugin> plugins,
            BuiltinInterceptorsFactory builtinInterceptorsFactory
    ) {
        return ImmutableMap.of(
                "StaticResponseHandler", new StaticResponseHandler.ConfigFactory(),
                "ConditionRouter", new ConditionRouter.ConfigFactory(),
                "BackendServiceProxy", new BackendServiceProxy.ConfigFactory(environment, backendRegistries(servicesFromConfig)),
                "InterceptorPipeline", new HttpInterceptorPipeline.ConfigFactory(plugins, builtinInterceptorsFactory),
                "ProxyToBackend", new ProxyToBackend.ConfigFactory(environment, new StyxBackendServiceClientFactory(environment))
        );
    }

    private static Map<String, Registry<BackendService>> backendRegistries(Map<String, StyxService> servicesFromConfig) {
        return servicesFromConfig.entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof Registry)
                .collect(toMap(Map.Entry::getKey, entry -> (Registry<BackendService>) entry.getValue()));
    }
}
