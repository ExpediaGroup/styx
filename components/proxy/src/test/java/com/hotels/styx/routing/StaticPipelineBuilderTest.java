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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.AbstractRegistry;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static rx.Observable.just;


public class StaticPipelineBuilderTest {

    private Environment environment;
    private BackendServiceClientFactory clientFactory;
    private Registry<BackendService> registry;


    @BeforeMethod
    public void staticPipelineBuilderTest() {
        environment = new Environment.Builder().build();
        clientFactory = (backendService, originsInventory, originStatsFactory) -> request -> just(response(OK).build());
        registry = backendRegistry(newBackendServiceBuilder().origins(newOriginBuilder("localhost", 0).build())
                .path("/foo").build());
    }

    @Test
    public void buildsInterceptorPipelineForBackendServices() throws Exception {

        HttpHandler handler = new StaticPipelineFactory(clientFactory, environment, registry, ImmutableList.of()).build();
        HttpResponse response = handler.handle(get("/foo").build(), HttpInterceptorContext.create()).asCompletableFuture().get();
        assertThat(response.status(), is(OK));
    }

    @Test
    public void appliesPluginsInOrderTheyAreConfigured() throws Exception {
        Iterable<NamedPlugin> plugins = ImmutableList.of(
                interceptor("Test-A", appendResponseHeader("X-From-Plugin", "A")),
                interceptor("Test-B", appendResponseHeader("X-From-Plugin", "B"))
        );

        HttpHandler handler = new StaticPipelineFactory(clientFactory, environment, registry, plugins).build();

        HttpResponse response = handler.handle(get("/foo").build(), HttpInterceptorContext.create()).asCompletableFuture().get();
        assertThat(response.status(), is(OK));
        assertThat(response.headers("X-From-Plugin"), hasItems("B", "A"));
    }

    private Registry<BackendService> backendRegistry(BackendService... backendServices) {
        return new TestRegistry(backendServices);
    }

    class TestRegistry extends AbstractRegistry<BackendService> {
        TestRegistry(BackendService... backendServices) {
            set(asList(backendServices));
        }

        @Override
        public CompletableFuture<ReloadResult> reload() {
            Changes<BackendService> build = new Changes.Builder<BackendService>().added().build();
            notifyListeners(build);
            return completedFuture(reloaded("ok"));
        }
    }

    private static NamedPlugin interceptor(String name, Plugin plugin) {
        return NamedPlugin.namedPlugin(name, plugin);
    }

    private static Plugin appendResponseHeader(String header, String value) {
        return (request, chain) -> chain.proceed(request).map(response -> response.newBuilder().addHeader(header, value).build());
    }

}