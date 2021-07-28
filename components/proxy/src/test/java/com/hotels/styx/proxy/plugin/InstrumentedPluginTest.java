/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static com.hotels.styx.api.Eventual.error;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.Metrics.formattedExceptionName;
import static com.hotels.styx.api.plugins.spi.Plugin.PASS_THROUGH;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    public void originalPluginShouldBePluginThatIsPassedIn() {
        Plugin mockPlugin = Mockito.mock(Plugin.class);
        InstrumentedPlugin plugin = instrumentedPlugin("pluginName", mockPlugin);
        assertThat(plugin.originalPlugin(), is(mockPlugin));
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

        assertThat(getStatusCount(pluginName, "500"), is(1.0));
        assertThat(getErrorCount(pluginName), is(1.0));
    }

    @Test
    public void metricIsRecordedWhenPluginReturnsErrorStatusEarly() {
        String pluginName = "returnEarly1";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName,
                (request, chain) -> aResponse(INTERNAL_SERVER_ERROR));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        verify(chain, never()).proceed(any(LiveHttpRequest.class));
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(getStatusCount(pluginName, "500"), is(1.0));
        assertThat(getErrorCount(pluginName), is(1.0));
    }

    @Test
    public void metricIsNotRecordedWhenErrorStatusIsReturnedByChain() {
        Chain chain = request -> aResponse(INTERNAL_SERVER_ERROR);
        String pluginName = "doNotRecordMe";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, PASS_THROUGH);

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(getStatusCount(pluginName, "500"), is(0.0));
        assertThat(getErrorCount(pluginName), is(0.0));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenResponseIsMappedToNon5005xxStatus() {
        Chain chain = request -> aResponse(OK);
        String pluginName = "replaceStatusCodeY";
        InstrumentedPlugin plugin = instrumentedPlugin("replaceStatusCodeY", (request, aChain) ->
                aChain.proceed(request)
                        .map(response -> responseWithNewStatusCode(response, BAD_GATEWAY)));

        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(getStatusCount(pluginName, "502"), is(1.0));
        assertThat(getErrorCount(pluginName), is(0.0));
    }

    @Test
    public void errorsMetricIsNotRecordedWhenPluginReturnsNon5005xxStatusEarly() {
        String pluginName = "returnEarly";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName,
                (request, chain) -> aResponse(BAD_GATEWAY));
        LiveHttpResponse response = Mono.from(plugin.intercept(someRequest, chain)).block();

        verify(chain, never()).proceed(any(LiveHttpRequest.class));
        assertThat(response.status(), is(BAD_GATEWAY));
        assertThat(getStatusCount(pluginName, "502"), is(1.0));
        assertThat(getErrorCount(pluginName), is(0.0));
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
        assertThat(getExceptionCount(pluginName, SOME_EXCEPTION), is(1.0));
        assertThat(getStatusCount(pluginName, "500"), is(1.0));
        assertThat(getErrorCount(pluginName), is(1.0));
    }

    @Test
    public void metricsAreRecordedWhenPluginReturnsException() {
        String pluginName = "immediateException";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, (request, chain) ->
                error(new SomeException()));

        assertThatEventualHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, chain));

        verify(chain, never()).proceed(any(LiveHttpRequest.class));
        assertThat(getExceptionCount(pluginName, SOME_EXCEPTION), is(1.0));
        assertThat(getStatusCount(pluginName, "500"), is(1.0));
        assertThat(getErrorCount(pluginName), is(1.0));
    }

    @Test
    public void metricsAreRecordedWhenPluginMapsToException() {
        String pluginName = "observableError";
        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, (request, chain) ->
                chain.proceed(request)
                        .flatMap(response -> error(new SomeException())));

        assertThatEventualHasErrorOnly(PluginException.class,
                plugin.intercept(someRequest, request -> aResponse(OK)));
        assertThat(getExceptionCount(pluginName, SOME_EXCEPTION), is(1.0));
        assertThat(getStatusCount(pluginName, "500"), is(1.0));
        assertThat(getErrorCount(pluginName), is(1.0));
    }

    @Test
    public void metricsAreNotRecordedWhenExceptionIsReturnedByChain() {
        String pluginName = "passThrough";
        Chain chain = request -> error(new SomeException());

        InstrumentedPlugin plugin = instrumentedPlugin(pluginName, PASS_THROUGH);

        assertThatEventualHasErrorOnly(SomeException.class,
                plugin.intercept(someRequest, chain));

        assertThat(getExceptionCount(pluginName, SOME_EXCEPTION), is(0.0));
        assertThat(getErrorCount(pluginName), is(0.0));
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

    private double getStatusCount(String pluginName, String status) {
        return Optional.ofNullable(registry.find("plugin.response")
                .tags("plugin", pluginName, "statusCode", status)
                .counter())
                .map(Counter::count)
                .orElse(0.0);
    }

    private double getErrorCount(String pluginName) {
        return Optional.ofNullable(registry.find("plugin.error")
                .tags("plugin", pluginName)
                .counter())
                .map(Counter::count)
                .orElse(0.0);
    }

    private double getExceptionCount(String pluginName, String type) {
        return Optional.ofNullable(registry.find("plugin.exception")
                .tags("plugin", pluginName, "type", type)
                .counter())
                .map(Counter::count)
                .orElse(0.0);
    }
}
