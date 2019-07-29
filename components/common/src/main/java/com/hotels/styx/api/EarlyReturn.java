/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.api;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Some convenience methods and values for early-return behaviour.
 * <p>
 * The main purpose of this is to ensure that the request is consumed, as well as cutting down on boilerplate.
 */
public final class EarlyReturn {

    /**
     * Consume request content and return an error.
     *
     * @param request live request
     * @param error   error
     * @return eventual live response
     */
    public static Eventual<LiveHttpResponse> returnEarlyWithError(LiveHttpRequest request, Throwable error) {
        return dropAndReplace(request, Eventual.error(error));
    }

    /**
     * Consume request content and return a response.
     *
     * @param request  live request
     * @param response live response
     * @return live response
     */
    public static Eventual<LiveHttpResponse> returnEarlyWithResponse(LiveHttpRequest request, LiveHttpResponse response) {
        return dropAndReplace(request, Eventual.of(response));
    }

    /**
     * Consume request content and return a response.
     *
     * @param request  live request
     * @param response non-live response
     * @return live response
     */
    public static Eventual<LiveHttpResponse> returnEarlyWithResponse(LiveHttpRequest request, HttpResponse response) {
        return dropAndReplace(request, Eventual.of(response.stream()));
    }

    private static <T> Eventual<T> dropAndReplace(LiveHttpRequest request, Publisher<T> other) {
        return new Eventual<>(
                Mono.from(request.<T>consume2())
                        .concatWith(other));
    }

    private EarlyReturn() {
    }
}
