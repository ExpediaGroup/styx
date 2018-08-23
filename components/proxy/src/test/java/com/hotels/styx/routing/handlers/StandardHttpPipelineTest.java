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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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
                .map(response -> {
                    foundInContext.set(
                            chain.context().get("contextValue", String.class));
                    return response;
                });

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
    public void sendsExceptionUponMultipleSubscription() throws Exception {
        HttpHandler handler = (request, context) -> StyxObservable.of(response(OK).build());

        StandardHttpPipeline pipeline = new StandardHttpPipeline(handler);

        StyxObservable<HttpResponse> responseObservable = pipeline.handle(get("/").build(), HttpInterceptorContext.create());
        HttpResponse response = responseObservable.asCompletableFuture().get();
        assertThat(response.status(), is(OK));

        toRxObservable(responseObservable).toBlocking().first();
    }

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "multipleSubscriptionInterceptors")
    public void sendsExceptionUponExtraSubscriptionInsideInterceptor(HttpInterceptor interceptor) throws Exception {
        HttpHandler handler = (request, context) -> StyxObservable.of(response(OK).build());

        List<HttpInterceptor> interceptors = singletonList(interceptor);
        StandardHttpPipeline pipeline = new StandardHttpPipeline(interceptors, handler);

        StyxObservable<HttpResponse> responseObservable = pipeline.handle(get("/").build(), HttpInterceptorContext.create());
        toRxObservable(responseObservable).toBlocking().first();
    }

    @DataProvider(name = "multipleSubscriptionInterceptors")
    private Object[][] multipleSubscriptionInterceptors() {
        return new Object[][]{
                {subscribeInPluginBeforeSubscription()}
//                , {reSubscribeDuringSubscription()}
//                , {reSubscribeDuringSubscriptionOriginalErrorCause()},
        };
    }

    private HttpInterceptor subscribeInPluginBeforeSubscription() {
        return (request, chain) -> {
            StyxObservable<HttpResponse> responseObservable = chain.proceed(request);

            await(responseObservable.asCompletableFuture());

            return responseObservable;
        };
    }

    // TOOD: Mikko: Styx 2.0 API: Probably can be removed because the
    // Rx Observables are not available for for API consumers.
//    private HttpInterceptor reSubscribeDuringSubscriptionOriginalErrorCause() {
//        return (request, chain) ->
//                just(request)
//                        .map(chain::proceed)
//                        .flatMap(responseObservable -> responseObservable
//                                .filter(response -> false)
//                                .switchIfEmpty(responseObservable));
//    }

//    private HttpInterceptor reSubscribeDuringSubscription() {
//        return (request, chain) ->
//                just(request).map(chain::proceed)
//                        .flatMap(responseObservable -> {
//                            responseObservable.toBlocking().single();
//
//                            return responseObservable;
//                        });
//    }

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

    private HttpResponse sendRequestTo(StandardHttpPipeline pipeline) {
        return await(pipeline.handle(get("/").build(), HttpInterceptorContext.create()).asCompletableFuture());
    }

    private StandardHttpPipeline pipeline(HttpInterceptor... interceptors) {
        return new StandardHttpPipeline(asList(interceptors), (request, context) -> StyxObservable.of(response(OK).build()));
    }
}