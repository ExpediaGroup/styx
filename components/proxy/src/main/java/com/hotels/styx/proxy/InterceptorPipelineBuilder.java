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
package com.hotels.styx.proxy;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.proxy.plugin.InstrumentedPlugin;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Builds a Styx HTTP pipeline NamedPlugins and a configured handler.
 */
public class InterceptorPipelineBuilder {
    private final Environment environment;
    private final Iterable<NamedPlugin> plugins;
    private final HttpHandler handler;

    public InterceptorPipelineBuilder(Environment environment, Iterable<NamedPlugin> plugins, HttpHandler handler) {
        this.environment = requireNonNull(environment);
        this.plugins = requireNonNull(plugins);
        this.handler = requireNonNull(handler);
    }

    public HttpHandler build() {
        List<HttpInterceptor> interceptors = ImmutableList.copyOf(instrument(plugins, environment));
        return new HttpInterceptorPipeline(interceptors, handler);
    }

    private static List<InstrumentedPlugin> instrument(Iterable<NamedPlugin> namedPlugins, Environment environment) {
        return stream(namedPlugins.spliterator(), false)
                .map(namedPlugin -> new InstrumentedPlugin(namedPlugin, environment))
                .collect(toList());
    }
}
