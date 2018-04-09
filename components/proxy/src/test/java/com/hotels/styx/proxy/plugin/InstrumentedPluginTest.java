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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.HttpInterceptor.Chain;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.support.api.SimpleEnvironment;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.plugins.spi.Plugin.PASS_THROUGH;
import static com.hotels.styx.proxy.plugin.InstrumentedPlugin.formattedExceptionName;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static rx.Observable.error;
import static rx.Observable.just;

public class InstrumentedPluginTest {
    private static final String SOME_EXCEPTION = formattedExceptionName(SomeException.class);

    private MetricRegistry metricRegistry;
    private Environment environment;
    private HttpRequest someRequest;
    private Chain chain;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();

        environment = new SimpleEnvironment.Builder()
                .metricRegistry(metricRegistry)
                .build();

        someRequest = get("/").build();
        chain = mock(Chain.class);
    }

    @Test
    public void metricIsRecordedWhenResponseIsMappedToErrorStatus() {
        Chain chain = request -> aResponse(OK);

        InstrumentedPlugin plugin = instrumentedPlugin("replaceStatusCode", (request, aChain) ->
                aChain.proceed(request)
                        .map(response -> responseWithNewStatusCode(response, INTERNAL_SERVER_ERROR)));

        HttpResponse response = plugin.intercept(someRequest, chain).toBlocking().single();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(metricRegistry.meter("plugins.replaceStatusCode.response.status.500").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.replaceStatusCode.errors").getCount(), is(1L));
    }

    @Test
    public void metricIsRecordedWhenPluginReturnsErrorStatusEarly() {
        InstrumentedPlugin plugin = instrumentedPlugin("returnEarly",
                (request, chain) -> aResponse(INTERNAL_SERVER_ERROR));

        HttpResponse response = plugin.intercept(someRequest, chain).toBlocking().single();

        verify(chain, never()).proceed(any(HttpRequest.class));
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(metricRegistry.meter("plugins.returnEarly.response.status.500").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.returnEarly.errors").getCount(), is(1L));
    }

    @Test
    public void metricIsNotRecordedWhenErrorStatusIsReturnedByChain() {
        Chain chain = request -> aResponse(INTERNAL_SERVER_ERROR);

        InstrumentedPlugin plugin = instrumentedPlugin("doNotRecordMe", PASS_THROUGH);

        HttpResponse response = plugin.intercept(someRequest, chain).toBlocking().single();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(metricRegistry.meter("plugins.doNotRecordMe.response.status.500").getCount(), is(0L));
        assertThat(metricRegistry.meter("plugins.doNotRecordMe.errors").getCount(), is(0L));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenResponseIsMappedToNon5005xxStatus() {
        Chain chain = request -> aResponse(OK);

        InstrumentedPlugin plugin = instrumentedPlugin("replaceStatusCode", (request, aChain) ->
                aChain.proceed(request)
                        .map(response -> responseWithNewStatusCode(response, BAD_GATEWAY)));

        HttpResponse response = plugin.intercept(someRequest, chain).toBlocking().single();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(metricRegistry.meter("plugins.replaceStatusCode.response.status.502").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.replaceStatusCode.errors").getCount(), is(0L));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenPluginReturnsNon5005xxStatusEarly() {
        InstrumentedPlugin plugin = instrumentedPlugin("returnEarly",
                (request, chain) -> aResponse(BAD_GATEWAY));

        HttpResponse response = plugin.intercept(someRequest, chain).toBlocking().single();

        verify(chain, never()).proceed(any(HttpRequest.class));
        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(metricRegistry.meter("plugins.returnEarly.response.status.502").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.returnEarly.errors").getCount(), is(0L));
    }

    @Test
    public void metricsAreRecordedWhenPluginThrowsException() {
        InstrumentedPlugin plugin = instrumentedPlugin("immediateException", (request, chain) -> {
            throw new SomeException();
        });

        assertThatObservableHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, chain));

        verify(chain, never()).proceed(any(HttpRequest.class));

        assertThat(metricRegistry.meter("plugins.immediateException.response.status.500").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.immediateException.exception." + SOME_EXCEPTION).getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.immediateException.errors").getCount(), is(1L));
    }

    @Test
    public void metricsAreRecordedWhenPluginReturnsException() {
        InstrumentedPlugin plugin = instrumentedPlugin("immediateException", (request, chain) ->
                error(new SomeException()));

        assertThatObservableHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, chain));

        verify(chain, never()).proceed(any(HttpRequest.class));

        assertThat(metricRegistry.meter("plugins.immediateException.response.status.500").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.immediateException.exception." + SOME_EXCEPTION).getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.immediateException.errors").getCount(), is(1L));
    }

    @Test
    public void metricsAreRecordedWhenPluginMapsToException() {
        InstrumentedPlugin plugin = instrumentedPlugin("observableError", (request, chain) ->
                chain.proceed(request)
                        .flatMap(response -> error(new SomeException())));

        assertThatObservableHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, request -> aResponse(OK)));

        assertThat(metricRegistry.meter("plugins.observableError.response.status.500").getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.observableError.exception." + SOME_EXCEPTION).getCount(), is(1L));
        assertThat(metricRegistry.meter("plugins.observableError.errors").getCount(), is(1L));
    }

    @Test
    public void metricsAreNotRecordedWhenExceptionIsReturnedByChain() {
        Chain chain = request -> error(new SomeException());

        InstrumentedPlugin plugin = instrumentedPlugin("passThrough", PASS_THROUGH);

        assertThatObservableHasErrorOnly(SomeException.class,
                plugin.intercept(someRequest, chain));

        assertThat(metricRegistry.meter("plugins.passThrough.exception." + SOME_EXCEPTION).getCount(), is(0L));
        assertThat(metricRegistry.meter("plugins.passThrough.errors").getCount(), is(0L));
    }

    private static Observable<HttpResponse> aResponse(HttpResponseStatus status) {
        return just(response(status).build());
    }

    private static <T> void assertThatObservableHasErrorOnly(Class<? extends Throwable> type, Observable<T> observable) {
        TestSubscriber<T> testSubscriber = new TestSubscriber<>();

        observable.subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoValues();
        testSubscriber.assertError(type);
    }

    private static HttpResponse responseWithNewStatusCode(HttpResponse response, HttpResponseStatus newStatus) {
        assertThat(response.status(), is(not(newStatus)));

        return response.newBuilder().status(newStatus).build();
    }

    private InstrumentedPlugin instrumentedPlugin(String name, Plugin plugin) {
        return new InstrumentedPlugin(namedPlugin(name, plugin), environment);
    }

    private static class SomeException extends RuntimeException {

    }
}