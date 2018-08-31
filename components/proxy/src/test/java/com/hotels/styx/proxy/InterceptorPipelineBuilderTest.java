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
import com.hotels.styx.AggregatedConfiguration;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.hotels.styx.api.HttpRequest;

public class InterceptorPipelineBuilderTest {
    private Environment environment;
    private Iterable<NamedPlugin> plugins;
    private HttpHandler handler;

    @BeforeMethod
    public void setUp() {
        environment = new Environment.Builder()
                .aggregatedConfiguration(new AggregatedConfiguration(StyxConfig.defaultConfig()))
                .build();
        plugins = ImmutableList.of(
                namedPlugin("plug1",
                        (request, chain) ->
                                chain.proceed(request)
                                        .map(response -> response.newBuilder()
                                                .header("plug1", "1")
                                                .build())),
                namedPlugin("plug2",
                        (request, chain) ->
                                chain.proceed(request)
                                        .map(response -> response.newBuilder()
                                                .header("plug2", "1")
                                                .build()))
        );

        handler = mock(HttpHandler.class);
        when(handler.handle(any(HttpRequest.class), any(HttpInterceptor.Context.class))).thenReturn(StyxObservable.of(response(OK).build()));
    }

    @Test
    public void buildsPipelineWithInterceptors() throws Exception {
        HttpHandler pipeline = new InterceptorPipelineBuilder(environment, plugins, handler).build();
        HttpResponse response = pipeline.handle(get("/foo").build(), HttpInterceptorContext.create()).asCompletableFuture().get();

        assertThat(response.header("plug1"), isValue("1"));
        assertThat(response.header("plug2"), isValue("1"));

        assertThat(response.status(), is(OK));
    }
}