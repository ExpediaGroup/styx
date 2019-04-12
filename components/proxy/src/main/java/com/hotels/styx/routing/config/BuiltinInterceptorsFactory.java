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
package com.hotels.styx.routing.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;

import java.util.Map;

import static java.lang.String.format;

/**
 * For Styx core to instantiate built in HTTP interceptors.
 *
 * Built-in HTTP interceptors are just like Styx plugins, but baked into Styx core,
 * and made available for end-users to configure in the HTTP interceptor pipeline.
 *
 */
public class BuiltinInterceptorsFactory {
    public static final ImmutableMap<String, HttpInterceptorFactory> INTERCEPTOR_FACTORIES =
            ImmutableMap.of("Rewrite", new RewriteInterceptor.Factory());

    private final Map<String, HttpInterceptorFactory> interceptorFactories;

    public BuiltinInterceptorsFactory() {
        this(INTERCEPTOR_FACTORIES);
    }

    public BuiltinInterceptorsFactory(Map<String, HttpInterceptorFactory> interceptorFactories) {
        this.interceptorFactories = interceptorFactories;
    }

    public HttpInterceptor build(RoutingObjectConfiguration configBlock) {
        if (configBlock instanceof RoutingObjectDefinition) {
            RoutingObjectDefinition block = (RoutingObjectDefinition) configBlock;
            String type = block.type();

            HttpInterceptorFactory constructor = interceptorFactories.get(type);
            Preconditions.checkArgument(constructor != null, format("Unknown handler type '%s'", type));

            return constructor.build(block);
        } else {
            throw new UnsupportedOperationException("Routing config node must be an config block, not a reference");
        }
    }
}
