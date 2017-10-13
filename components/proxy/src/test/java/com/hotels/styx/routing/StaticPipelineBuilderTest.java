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
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.AbstractRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import org.testng.annotations.Test;

import java.util.function.Supplier;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static rx.Observable.just;


public class StaticPipelineBuilderTest {

    private final Environment environment = new Environment.Builder().build();
    private final BackendServiceClientFactory clientFactory;
    private final Registry<BackendService> registry;

    public StaticPipelineBuilderTest() {
        clientFactory = backendService -> request -> just(response(OK).build());
        registry = backendRegistry(newBackendServiceBuilder().path("/foo").build());
    }

    @Test
    public void buildsInterceptorPipelineForBackendServices() throws Exception {

        HttpHandler2 handler = new StaticPipelineFactory(clientFactory, environment, registry, ImmutableList::of).build();

        HttpResponse response = handler.handle(get("/foo").build(), HttpInterceptor.Context.EMPTY).toBlocking().first();
        assertThat(response.status(), is(OK));
    }

    @Test
    public void appliesPluginsInOrderTheyAreConfigured() throws Exception {
        Supplier<Iterable<NamedPlugin>> pluginsSupplier = () -> ImmutableList.of(
                interceptor("Test-A", appendResponseHeader("X-From-Plugin", "A")),
                interceptor("Test-B", appendResponseHeader("X-From-Plugin", "B"))
        );

        HttpHandler2 handler = new StaticPipelineFactory(clientFactory, environment, registry, pluginsSupplier).build();

        HttpResponse response = handler.handle(get("/foo").build(), HttpInterceptor.Context.EMPTY).toBlocking().first();
        assertThat(response.status(), is(OK));
        assertThat(response.headers("X-From-Plugin"), hasItems("B", "A"));
    }

    private Registry<BackendService> backendRegistry(BackendService... backendServices) {
        TestRegistry registry = new TestRegistry(backendServices);
        registry.startAsync().awaitRunning();
        return registry;
    }

    class TestRegistry extends AbstractRegistry<BackendService> {
        public TestRegistry(BackendService... backendServices) {
            snapshot.set(asList(backendServices));
        }

        @Override
        public void reload(ReloadListener listener) {
            Changes<BackendService> build = new Changes.Builder<BackendService>().added().build();
            notifyListeners(build);
        }
    }

    private NamedPlugin interceptor(String name, Plugin plugin) {
        return NamedPlugin.namedPlugin(name, plugin);
    }

    private Plugin appendResponseHeader(String header, String value) {
        return (request, chain) -> chain.proceed(request).map(response -> response.newBuilder().addHeader(header, value).build());
    }

}