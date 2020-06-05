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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.Environment;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor.Chain;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


import static com.hotels.styx.api.Eventual.error;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.plugins.spi.Plugin.PASS_THROUGH;
import static com.hotels.styx.proxy.plugin.InstrumentedPlugin.formattedExceptionName;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class InstrumentedPluginTest {
    private static final String SOME_EXCEPTION = formattedExceptionName(SomeException.class);

    private MeterRegistry registry;
    private Environment environment;
    private LiveHttpRequest someRequest;
    private Chain chain;

    @BeforeEach
    public void setUp() {
        registry = new SimpleMeterRegistry();

        environment = new Environment.Builder()
                .registry(registry)
                .build();


        someRequest = get("/").build();
        chain = mock(Chain.class);
    }

    @Test
    public void metricIsRecordedWhenResponseIsMappedToErrorStatus() {
        Chain chain = request -> aResponse(OK);
        String pluginName = "replaceStatus1";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, (request, aChain) ->
                aChain.proceed(request)
                        .map(response -> responseWithNewStatusCode(response, INTERNAL_SERVER_ERROR)));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));

        assertThat(Metrics.counter("plugins." + pluginName + ".response.status.500").count(), is(1.0));
        assertThat(Metrics.counter("plugins." + pluginName + ".errors").count(), is(1.0));
    }

    @Test
    public void metricIsRecordedWhenPluginReturnsErrorStatusEarly() {
        String pluginName = "returnEarly1";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName,
                (request, chain) -> aResponse(INTERNAL_SERVER_ERROR));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        verify(chain, never()).proceed(any(LiveHttpRequest.class));
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(Metrics.counter("plugins." + pluginName + ".response.status.500").count(), is(1.0));
        assertThat(Metrics.counter("plugins." + pluginName + ".errors").count(), is(1.0));
    }

    @Test
    public void metricIsNotRecordedWhenErrorStatusIsReturnedByChain() {
        Chain chain = request -> aResponse(INTERNAL_SERVER_ERROR);

        InstrumentedPlugin plugin = instrumentedPlugin("doNotRecordMe", PASS_THROUGH);

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(Metrics.counter("plugins.doNotRecordMe.response.status.500").count(), is(0.0));
        assertThat(Metrics.counter("plugins.doNotRecordMe.errors").count(), is(0.0));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenResponseIsMappedToNon5005xxStatus() {
        Chain chain = request -> aResponse(OK);

        InstrumentedPlugin plugin = instrumentedPlugin("replaceStatusCodeY", (request, aChain) ->
                aChain.proceed(request)
                        .map(response -> responseWithNewStatusCode(response, BAD_GATEWAY)));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(Metrics.counter("plugins.replaceStatusCodeY.response.status.502").count(), is(1.0));
        assertThat(Metrics.counter("plugins.replaceStatusCodeY.errors").count(), is(0.0));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenPluginReturnsNon5005xxStatusEarly() {
        InstrumentedPlugin plugin = instrumentedPlugin("returnEarly",
                (request, chain) -> aResponse(BAD_GATEWAY));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        verify(chain, never()).proceed(any(LiveHttpRequest.class));
        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(Metrics.counter("plugins.returnEarly.response.status.502").count(), is(1.0));
        assertThat(Metrics.counter("plugins.returnEarly.errors").count(), is(0.0));
    }

    @Test
    public void metricsAreRecordedWhenPluginThrowsException() {
        String pluginName = "immediateException1";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, (request, chain) -> {
            throw new SomeException();
        });

        assertThatEventualHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, chain));

        verify(chain, never()).proceed(any(LiveHttpRequest.class));

        assertThat(Metrics.counter("plugins." + pluginName + ".response.status.500").count(), is(1.0));
        assertThat(Metrics.counter("plugins." + pluginName + ".exception." + SOME_EXCEPTION).count(), is(1.0));
        assertThat(Metrics.counter("plugins." + pluginName + ".errors").count(), is(1.0));
    }

    @Test
    public void metricsAreRecordedWhenPluginReturnsException() {
        InstrumentedPlugin plugin = instrumentedPlugin("immediateException", (request, chain) ->
                error(new SomeException()));

        assertThatEventualHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, chain));

        verify(chain, never()).proceed(any(LiveHttpRequest.class));

        assertThat(Metrics.counter("plugins.immediateException.response.status.500").count(), is(1.0));
        assertThat(Metrics.counter("plugins.immediateException.exception." + SOME_EXCEPTION).count(), is(1.0));
        assertThat(Metrics.counter("plugins.immediateException.errors").count(), is(1.0));
    }

    @Test
    public void metricsAreRecordedWhenPluginMapsToException() {
        InstrumentedPlugin plugin = instrumentedPlugin("observableError", (request, chain) ->
                chain.proceed(request)
                        .flatMap(response -> error(new SomeException())));

        assertThatEventualHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, request -> aResponse(OK)));

        assertThat(Metrics.counter("plugins.observableError.response.status.500").count(), is(1.0));
        assertThat(Metrics.counter("plugins.observableError.exception." + SOME_EXCEPTION).count(), is(1.0));
        assertThat(Metrics.counter("plugins.observableError.errors").count(), is(1.0));
    }

    @Test
    public void metricsAreNotRecordedWhenExceptionIsReturnedByChain() {
        Chain chain = request -> error(new SomeException());

        InstrumentedPlugin plugin = instrumentedPlugin("passThrough", PASS_THROUGH);

        assertThatEventualHasErrorOnly(SomeException.class,
                plugin.intercept(someRequest, chain));

        assertThat(Metrics.counter("plugins.passThrough.exception." + SOME_EXCEPTION).count(), is(0.0));
        assertThat(Metrics.counter("plugins.passThrough.errors").count(), is(0.0));
    }

    private static Eventual<LiveHttpResponse> aResponse(HttpResponseStatus status) {
        return Eventual.of(response(status).build());
    }

    private static <T> void assertThatEventualHasErrorOnly(Class<? extends Throwable> type, Eventual<T> eventual) {
        StepVerifier.create(eventual)
                .expectError(type)
                .verify();
    }

    private static LiveHttpResponse responseWithNewStatusCode(LiveHttpResponse response, HttpResponseStatus newStatus) {
        assertThat(response.status(), is(not(newStatus)));

        return response.newBuilder().status(newStatus).build();
    }

    private InstrumentedPlugin instrumentedPlugin(String name, Plugin plugin) {
        return new InstrumentedPlugin(namedPlugin(name, plugin), environment);
    }

    private static class SomeException extends RuntimeException {

    }
}
