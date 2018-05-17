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

import rx.Observable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Exposes a transformation API for HTTP interceptors.
 *
 * This interface provides a is *not* intended for plugins to extend.
 *
 * @param <T>
 */
public interface StyxObservable<T> {
    <U> StyxObservable<U> map(Function<T, U> transformation);
    <U> StyxObservable<U> flatMap(Function<T, StyxObservable<U>> transformation);
    <U> StyxObservable<U> reduce(BiFunction<T, U, U> accumulator, U initialValue);

    // TODO: Mikko: Styx 2.0 Api: `onError`: is more flexible type signature possible? Such as:
    //       <U> StyxObservable<U> onError(Function<Throwable, StyxObservable<U>> errorHandler);
    StyxObservable<T> onError(Function<Throwable, StyxObservable<T>> errorHandler);

    CompletableFuture<T> asCompletableFuture();

    // Static Factory Methods

    static <T> StyxObservable<T> of(T value) {
        return new StyxCoreObservable<>(Observable.just(value));
    }

    // TODO: Mikko: Only required for testing?
    static <T> StyxObservable<T> empty() {
        return new StyxCoreObservable<T>(Observable.empty());
    }

    static <T> StyxObservable<T> from(Iterable<T> values) {
        return new StyxCoreObservable<>(Observable.from(values));
    }

    static <T> StyxObservable<T> from(CompletionStage<T> completableFuture) {
        return new StyxCoreObservable<>(completableFuture);
    }

    static <T> StyxObservable<T> error(Throwable error) {
        return new StyxCoreObservable<>(Observable.error(error));
    }

}
