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
package com.hotels.styx.api;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Helper methods for Styx HTTP Message objects.
 */
public final class Requests {
    private Requests() {
    }

    public static LiveHttpRequest doFinally(LiveHttpRequest request, Consumer<Optional<Throwable>> action) {
        return request.newBuilder()
                .body(it -> it.doOnEnd(action))
                .build();
    }

    public static LiveHttpResponse doFinally(LiveHttpResponse response, Consumer<Optional<Throwable>> action) {
        return response.newBuilder()
                .body(it -> it.doOnEnd(action::accept))
                .build();
    }

    public static LiveHttpRequest doOnComplete(LiveHttpRequest request, Runnable action) {
        return request.newBuilder()
                .body(it -> it.doOnEnd(ifSuccessful(action)))
                .build();
    }

    public static LiveHttpResponse doOnComplete(LiveHttpResponse response, Runnable action) {
        return response.newBuilder()
                .body(it -> it.doOnEnd(ifSuccessful(action)))
                .build();
    }

    public static LiveHttpRequest doOnError(LiveHttpRequest request, Consumer<Throwable> action) {
        return request.newBuilder()
                .body(it -> it.doOnEnd(ifError(action)))
                .build();
    }

    public static LiveHttpResponse doOnError(LiveHttpResponse response, Consumer<Throwable> action) {
        return response.newBuilder()
                .body(it -> it.doOnEnd(ifError(action)))
                .build();
    }

    public static LiveHttpRequest doOnCancel(LiveHttpRequest request, Runnable action) {
        return request.newBuilder().body(it -> it.doOnCancel(action)).build();
    }

    public static LiveHttpResponse doOnCancel(LiveHttpResponse response, Runnable action) {
        return response.newBuilder().body(it -> it.doOnCancel(action)).build();
    }

    private static Consumer<Optional<Throwable>> ifError(Consumer<Throwable> action) {
        return (maybeCause) -> maybeCause.ifPresent(action);
    }

    private static Consumer<Optional<Throwable>> ifSuccessful(Runnable action) {
        return (maybeCause) -> {
            if (!maybeCause.isPresent()) {
                action.run();
            }
        };
    }



}
