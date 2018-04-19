/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.api.v2;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpInterceptorTest {

//    @Test
//    public void passThrough() throws ExecutionException, InterruptedException {
//        HttpInterceptor interceptor = (request, chain) -> chain.proceed(request);
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) interceptor.intercept(newRequest("req-01"), mockChain()))
//                .asCompletableFuture()
//                .get();
//
//        assertThat(response.header("X-Request-Id"), is(Optional.of("req-01")));
//    }

//    @Test
//    public void synchronousRequestTransformation() throws ExecutionException, InterruptedException {
//        HttpInterceptor interceptor = (request, chain) -> chain.proceed(
//                request.newBuilder()
//                        .header("X-Test-Header", "y")
//                        .build());
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) interceptor.intercept(newRequest("req-02"), mockChain()))
//                .asCompletableFuture()
//                .get();
//
//        assertThat(response.header("X-Request-Id"), is(Optional.of("req-02")));
//    }
//
//    TODO: Mikko: Styx 2.0 API: How to create a StyxObservable for unit tests?
//
//    @Test
//    public void asynchronousRequestTransformation() throws ExecutionException, InterruptedException {
//
//        //
//        // Asynchronous behaviour in the upstream direction
//        //
//        // Note that "flatMap" etc imply asynchronous transformation in downstream direction
//        // and therefor are somewhat confusing to use.
//        //
//        HttpInterceptor interceptor = (request, chain) -> StyxObservable.from(asyncIdProvider("req-03"))
//                .transformAsync(newId -> chain.proceed(
//                        request.newBuilder()
//                                .id(newId)
//                                .build()));
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) interceptor.intercept(newRequest(""), mockChain()))
//                .asCompletableFuture()
//                .get();
//
//        assertThat(response.header("X-Request-Id"), is(Optional.of("req-03")));
//    }

//    @Test
//    public void synchronousResponseTransformation() throws ExecutionException, InterruptedException {
//        HttpInterceptor interceptor = (request, chain) -> chain.proceed(request)
//                .map(response -> response
//                        .newBuilder()
//                        .header("X-Added", "True")
//                        .build());
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) interceptor.intercept(newRequest("req-04"), mockChain()))
//                .asCompletableFuture()
//                .get();
//
//        assertThat(response.header("X-Request-Id"), is(Optional.of("req-04")));
//        assertThat(response.header("X-Added"), is(Optional.of("True")));
//    }

//    @Test
//    public void asynchronousResponseTransformation() throws ExecutionException, InterruptedException {
//        HttpInterceptor interceptor = (request, chain) -> chain.proceed(request)
//                .flatMap(response -> StyxObservable.from(
//                        completedFuture(
//                                response.newBuilder()
//                                        .header("X-Added", "True")
//                                        .build())));
//
//        HttpResponse response = ((StyxCoreObservable<HttpResponse>) interceptor.intercept(newRequest("req-05"), mockChain()))
//                .asCompletableFuture()
//                .get();
//
//        assertThat(response.header("X-Request-Id"), is(Optional.of("req-05")));
//        assertThat(response.header("X-Added"), is(Optional.of("True")));
//    }

    CompletableFuture<String> asyncIdProvider(String id) {
        return completedFuture(id);
    }

    HttpRequest newRequest(String id) {
        return get("/").id(id).build();
    }

//    private static HttpInterceptor.Chain mockChain() {
//        return request -> StyxObservable.from(completedFuture(
//                response(OK)
//                        .header("X-Request-Id", request.id())
//                        .build()));
//    }
}
