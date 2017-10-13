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
package com.hotels.styx.support.api;

import com.hotels.styx.api.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;

import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static rx.Observable.error;
import static rx.Observable.just;

public final class BlockingObservables {

    private static final int MEGABYTE = 1024 * 1024;
    private static final int TEN_MEGABYTES = 10 * MEGABYTE;

    public static <T> T getFirst(Observable<T> observable) {
        return observable.toBlocking().single();
    }

    public static HttpResponse responseHeaders(Observable<HttpResponse> responseObs) {
        return responseObs.flatMap(
                response -> response.decode(BlockingObservables::toUtf8, TEN_MEGABYTES)
                        .map(decodedResponse -> decodedResponse.responseBuilder().build())
        ).toBlocking().single();
    }

    public static HttpResponse.DecodedResponse<String> stringResponse(Observable<HttpResponse> responseObs) {
        return stringResponse(responseObs, 10 * MEGABYTE);
    }

    public static HttpResponse.DecodedResponse<String> stringResponse(Observable<HttpResponse> responseObs, int maxSize) {
        return responseObs.flatMap(
                response -> response.decode(BlockingObservables::toUtf8, maxSize)
        ).toBlocking().single();
    }

    public static String decodeAsString(HttpResponse response) {
        return decodeAsString(response, MEGABYTE);
    }

    public static String decodeAsString(HttpResponse response, int maxSize) {
        return response.decode(byteBuf -> byteBuf.toString(UTF_8), maxSize).toBlocking().single().body();
    }

    public static String responseBody(Observable<HttpResponse> responseObs, Consumer<HttpResponse> headersAssertions, int maxSize) {
        return responseObs
                .flatMap(response -> applyAssertionsAndEnhanceErrors(response, headersAssertions, maxSize))
                .flatMap(response -> response.decode(BlockingObservables::toUtf8, maxSize))
                .map(HttpResponse.DecodedResponse::body)
                .toBlocking()
                .single();
    }

    private static Observable<HttpResponse> applyAssertionsAndEnhanceErrors(HttpResponse response, Consumer<HttpResponse> headersAssertions, int maxSize) {
        return applyAssertions(response, headersAssertions)
                .onErrorResumeNext(error -> failureWithBody(response, maxSize, error));
    }

    private static Observable<HttpResponse> applyAssertions(HttpResponse response, Consumer<HttpResponse> headersAssertions) {
        try {
            headersAssertions.accept(response);
            return just(response);
        } catch (Throwable t) {
            return error(t);
        }
    }

    private static Observable<HttpResponse> failureWithBody(HttpResponse response, int maxSize, Throwable error) {
        return response
                .decode(BlockingObservables::toUtf8, maxSize)
                .flatMap(decodedResponse ->
                        error(new AssertionError(error.getMessage() + ", body = " + decodedResponse.body(), error)));
    }

    private static String toUtf8(ByteBuf buf) {
        return buf.toString(UTF_8);
    }

    public static String responseBody(Observable<HttpResponse> responseObs, Consumer<HttpResponse> headersAssertions) {
        return responseBody(responseObs, headersAssertions, TEN_MEGABYTES);
    }

    public static String responseBody(Observable<HttpResponse> responseObs) {
        return responseBody(responseObs, response -> {
        }, TEN_MEGABYTES);
    }

    public static String responseBody(Observable<HttpResponse> responseObs, HttpResponseStatus expectedStatus) {
        return responseBody(responseObs, response -> assertThat(response.status(), is(expectedStatus)));
    }

    private BlockingObservables() {
    }
}
