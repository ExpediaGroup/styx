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
package com.hotels.styx.routing.handlers;

import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static rx.Observable.just;

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

        HttpResponse response = sendRequestTo(pipeline);

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

        HttpResponse response = sendRequestTo(pipeline);

        assertThat(response.status(), is(OK));
        assertThat(foundInContext.get(), is("expected"));
    }

    @Test
    public void interceptorsCanPassInformationThroughContextAfterRequest() {
        HttpInterceptor addsToContext = (request, chain) ->
                chain.proceed(request)
                        .doOnNext(response -> chain.context().add("contextValue", "expected"));

        AtomicReference<String> foundInContext = new AtomicReference<>();

        HttpInterceptor takesFromContext = (request, chain) -> chain.proceed(request)
                .doOnNext(response -> foundInContext.set(
                        chain.context().get("contextValue", String.class)));

        // add + take happens on the way back, so order must be reserved
        StandardHttpPipeline pipeline = pipeline(takesFromContext, addsToContext);

        HttpResponse response = sendRequestTo(pipeline);

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
                .doOnNext(response -> foundInContext.set(
                        chain.context().get("contextValue", String.class)));

        StandardHttpPipeline pipeline = pipeline(addsToContext, takesFromContext);

        HttpResponse response = sendRequestTo(pipeline);

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

    @Test(expectedExceptions = IllegalStateException.class)
    public void sendsExceptionUponMultipleSubscription() {
        HttpHandler2 handler = (request, context)-> just(response(OK).build());

        StandardHttpPipeline pipeline = new StandardHttpPipeline(handler);

        Observable<HttpResponse> responseObservable = pipeline.handle(get("/").build(), HttpInterceptorContext.create());
        HttpResponse response = responseObservable.toBlocking().single();
        assertThat(response.status(), is(OK));

        responseObservable.toBlocking().single();
    }

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "multipleSubscriptionInterceptors")
    public void sendsExceptionUponExtraSubscriptionInsideInterceptor(HttpInterceptor interceptor) {
        HttpHandler2 handler = (request, context) -> just(response(OK).build());

        List<HttpInterceptor> interceptors = singletonList(interceptor);
        StandardHttpPipeline pipeline = new StandardHttpPipeline(interceptors, handler);

        Observable<HttpResponse> responseObservable = pipeline.handle(get("/").build(), HttpInterceptorContext.create());
        responseObservable.toBlocking().single();
    }

    @DataProvider(name = "multipleSubscriptionInterceptors")
    private Object[][] multipleSubscriptionInterceptors() {
        return new Object[][]{
                {subscribeInPluginBeforeSubscription()},
                {reSubscribeDuringSubscription()},
                {reSubscribeDuringSubscriptionOriginalErrorCause()},
        };
    }

    private HttpInterceptor subscribeInPluginBeforeSubscription() {
        return (request, chain) -> {
            Observable<HttpResponse> responseObservable = chain.proceed(request);

            responseObservable.toBlocking().single();

            return responseObservable;
        };
    }

    private HttpInterceptor reSubscribeDuringSubscriptionOriginalErrorCause() {
        return (request, chain) ->
                just(request).map(chain::proceed)
                        .flatMap(responseObservable -> responseObservable
                                .filter(response -> false)
                                .switchIfEmpty(responseObservable));
    }

    private HttpInterceptor reSubscribeDuringSubscription() {
        return (request, chain) ->
                just(request).map(chain::proceed)
                        .flatMap(responseObservable -> {
                            responseObservable.toBlocking().single();

                            return responseObservable;
                        });
    }

    private HttpInterceptor recordingInterceptor(String name, Consumer<String> onInterceptRequest, Consumer<String> onInterceptResponse) {
        return (request, chain) -> {
            onInterceptRequest.accept(name);
            return chain.proceed(request)
                    .doOnNext(response -> onInterceptResponse.accept(name));
        };
    }

    private HttpResponse sendRequestTo(StandardHttpPipeline pipeline) {
        return pipeline.handle(get("/").build(), HttpInterceptorContext.create()).toBlocking().first();
    }

    private StandardHttpPipeline pipeline(HttpInterceptor... interceptors) {
        return new StandardHttpPipeline(asList(interceptors), (request, context) -> just(response(OK).build()));
    }
}