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
package com.hotels.styx.routing.handlers;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.server.track.RequestTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StandardHttpPipelineTest {
    @Test
    public void passesThroughAllInterceptors() {
        List<String> requestReceivers = new ArrayList<>();
        List<String> responseReceivers = new ArrayList<>();

        StandardHttpPipeline pipeline = pipeline(
                recordingInterceptor("interceptor 1", requestReceivers::add, responseReceivers::add),
                recordingInterceptor("interceptor 2", requestReceivers::add, responseReceivers::add),
                recordingInterceptor("interceptor 3", requestReceivers::add, responseReceivers::add)
        );

        LiveHttpResponse response = sendRequestTo(pipeline);

        assertThat(response.status(), is(OK));

        assertThat(requestReceivers, contains("interceptor 1", "interceptor 2", "interceptor 3"));
        assertThat(responseReceivers, contains("interceptor 3", "interceptor 2", "interceptor 1"));
    }

    @Test
    public void interceptorsCanPassInformationThroughContextBeforeRequest() {
        HttpInterceptor addsToContext = (request, chain) -> {
            chain.context().add("contextValue", "expected");
            return chain.proceed(request);
        };

        AtomicReference<String> foundInContext = new AtomicReference<>();

        HttpInterceptor takesFromContext = (request, chain) -> {
            foundInContext.set(chain.context().get("contextValue", String.class));
            return chain.proceed(request);
        };

        StandardHttpPipeline pipeline = pipeline(addsToContext, takesFromContext);

        LiveHttpResponse response = sendRequestTo(pipeline);

        assertThat(response.status(), is(OK));
        assertThat(foundInContext.get(), is("expected"));
    }

    @Test
    public void interceptorsCanPassInformationThroughContextAfterRequest() {
        HttpInterceptor addsToContext = (request, chain) ->
                chain.proceed(request)
                        .map(response -> {
                            chain.context().add("contextValue", "expected");
                            return response;
                        });

        AtomicReference<String> foundInContext = new AtomicReference<>();

        HttpInterceptor takesFromContext = (request, chain) -> chain.proceed(request)
                .map(response -> {
                    foundInContext.set(
                            chain.context().get("contextValue", String.class));
                    return response;
                });

        // add + take happens on the way back, so order must be reserved
        StandardHttpPipeline pipeline = pipeline(takesFromContext, addsToContext);

        LiveHttpResponse response = sendRequestTo(pipeline);

        assertThat(response.status(), is(OK));
        assertThat(foundInContext.get(), is("expected"));
    }

    @Test
    public void contextValuesAddedBeforeRequestCanBeRetrievedAfterward() {
        HttpInterceptor addsToContext = (request, chain) -> {
            chain.context().add("contextValue", "expected");
            return chain.proceed(request);
        };

        AtomicReference<String> foundInContext = new AtomicReference<>();

        HttpInterceptor takesFromContext = (request, chain) -> chain.proceed(request)
                .map(response -> {
                    foundInContext.set(
                            chain.context().get("contextValue", String.class));
                    return response;
                });

        StandardHttpPipeline pipeline = pipeline(addsToContext, takesFromContext);

        LiveHttpResponse response = sendRequestTo(pipeline);

        assertThat(response.status(), is(OK));
        assertThat(foundInContext.get(), is("expected"));
    }

    @Test
    public void interceptorReceivesNewContext() {
        HttpInterceptor expectNewContext = (request, chain) -> {
            Object seen = chain.context().get("seen", Object.class);
            assertThat("Old context reused" + seen, seen, is(nullValue()));
            chain.context().add("seen", true);
            return chain.proceed(request);
        };

        StandardHttpPipeline pipeline = pipeline(expectNewContext);

        assertThat(sendRequestTo(pipeline).status(), is(OK));
        // make the same request again to ensure a new context is used
        assertThat(sendRequestTo(pipeline).status(), is(OK));
    }

    @Test
    public void sendsExceptionUponMultipleSubscription() {
        HttpHandler handler = (request, context) -> Eventual.of(response(OK).build());

        StandardHttpPipeline pipeline = new StandardHttpPipeline(handler);

        Eventual<LiveHttpResponse> responseObservable = pipeline.handle(get("/").build(), requestContext());
        LiveHttpResponse response = Mono.from(responseObservable).block();
        assertThat(response.status(), is(OK));

        assertThrows(IllegalStateException.class,
                () -> Mono.from(responseObservable).block());
    }

    @ParameterizedTest
    @MethodSource("multipleSubscriptionInterceptors")
    public void sendsExceptionUponExtraSubscriptionInsideInterceptor(HttpInterceptor interceptor) throws Exception {
        HttpHandler handler = (request, context) -> Eventual.of(response(OK).build());

        List<HttpInterceptor> interceptors = singletonList(interceptor);
        StandardHttpPipeline pipeline = new StandardHttpPipeline(interceptors, handler, RequestTracker.NO_OP);

        Eventual<LiveHttpResponse> responseObservable = pipeline.handle(get("/").build(), requestContext());
        assertThrows(IllegalStateException.class,
                () -> Mono.from(responseObservable).block());
    }

    private static Stream<Arguments> multipleSubscriptionInterceptors() {
        return Stream.of(
                Arguments.of(subscribeInPluginBeforeSubscription())
        );
    }

    private static HttpInterceptor subscribeInPluginBeforeSubscription() {
        return (request, chain) -> {
            Eventual<LiveHttpResponse> responseObservable = chain.proceed(request);

            Mono.from(responseObservable).block();

            return responseObservable;
        };
    }

    private HttpInterceptor recordingInterceptor(String name, Consumer<String> onInterceptRequest, Consumer<String> onInterceptResponse) {
        return (request, chain) -> {
            onInterceptRequest.accept(name);
            return chain.proceed(request)
                    .map(response -> {
                        onInterceptResponse.accept(name);
                        return response;
                    });
        };
    }

    private LiveHttpResponse sendRequestTo(StandardHttpPipeline pipeline) {
        HttpInterceptor.Context context = new HttpInterceptorContext(false, InetSocketAddress.createUnresolved("127.0.0.1", 0), Runnable::run);

        return Mono.from(pipeline.handle(get("/").build(), context)).block();
    }

    private StandardHttpPipeline pipeline(HttpInterceptor... interceptors) {
        return new StandardHttpPipeline(asList(interceptors), (request, context) -> Eventual.of(response(OK).build()), RequestTracker.NO_OP);
    }
}