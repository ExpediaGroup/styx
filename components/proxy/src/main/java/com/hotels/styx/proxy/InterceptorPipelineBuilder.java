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
package com.hotels.styx.proxy;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.proxy.plugin.InstrumentedPlugin;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;

import java.util.List;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Builds a Styx HTTP pipeline NamedPlugins and a configured handler.
 */
public class InterceptorPipelineBuilder {
    private final Environment environment;
    private final Supplier<Iterable<NamedPlugin>> pluginSupplier;
    private final HttpHandler2 handler;

    public InterceptorPipelineBuilder(Environment environment, Supplier<Iterable<NamedPlugin>> pluginSupplier, HttpHandler2 handler) {
        this.environment = checkNotNull(environment);
        this.pluginSupplier = checkNotNull(pluginSupplier);
        this.handler = checkNotNull(handler);
    }

    public HttpHandler2 build() {
        List<HttpInterceptor> interceptors = ImmutableList.copyOf(instrument(pluginSupplier.get(), environment));
        return new HttpInterceptorPipeline(interceptors, handler);
    }

    private static List<InstrumentedPlugin> instrument(Iterable<NamedPlugin> namedPlugins, Environment environment) {
        return stream(namedPlugins.spliterator(), false)
                .map(namedPlugin -> new InstrumentedPlugin(namedPlugin, environment))
                .collect(toList());
    }
}
