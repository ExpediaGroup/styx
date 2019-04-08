/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.proxy.StyxBackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.handlers.ConditionRouter;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.routing.handlers.ProxyToBackend;
import com.hotels.styx.routing.handlers.StaticResponseHandler;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Built in HTTP handlers.
 */
public class BuiltInRoutingObjects {
    public static ImmutableMap<String, HttpHandlerFactory> createBuiltinRoutingObjectFactories(
            Environment environment,
            Iterable<NamedPlugin> plugins,
            BuiltinInterceptorsFactory builtinInterceptorsFactory,
            boolean requestTracking) {
        return ImmutableMap.<String, HttpHandlerFactory>builder()
                .put("StaticResponseHandler", new StaticResponseHandler.Factory())
                .put("ConditionRouter", new ConditionRouter.Factory())
                .put("InterceptorPipeline", new HttpInterceptorPipeline.Factory(plugins, builtinInterceptorsFactory, requestTracking))
                .put("ProxyToBackend", new ProxyToBackend.Factory(environment, new StyxBackendServiceClientFactory(environment)))
                .build();

    }

    private static Map<String, Registry<BackendService>> backendRegistries(Map<String, StyxService> servicesFromConfig) {
        return servicesFromConfig.entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof Registry)
                .collect(toMap(Map.Entry::getKey, entry -> (Registry<BackendService>) entry.getValue()));
    }

}
