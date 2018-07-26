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
 * <p>
 * This interface provides a is *not* intended for plugins to extend.
 *
 * @param <T> type of object published
 */
public interface StyxObservable<T> {
    <R> StyxObservable<R> map(Function<? super T, ? extends R> transformation);

    <R> StyxObservable<R> flatMap(Function<? super T, ? extends StyxObservable<? extends R>> transformation);

    <R> StyxObservable<R> reduce(BiFunction<? super T, R, R> accumulator, R initialValue);

    StyxObservable<T> onError(Function<Throwable, ? extends StyxObservable<? extends T>> errorHandler);

    /**
     * Converts this observable to a completable future. Note that in order to do this, it must
     * publish exactly one element before completing, otherwise the future will complete exceptionally,
     * with the error being:
     *
     * <ul>
     * <li>
     * {@link java.util.NoSuchElementException} if it completes without publishing any elements.
     * </li>
     * <li>
     * {@link IllegalArgumentException} if more than one element is published.
     * </li>
     * </ul>
     *
     * @return a completable future
     */
    CompletableFuture<T> asCompletableFuture();

    // Static Factory Methods

    static <T> StyxObservable<T> of(T value) {
        return new StyxCoreObservable<>(Observable.just(value));
    }

    static <T> StyxObservable<T> from(Iterable<? extends T> values) {
        return new StyxCoreObservable<>(Observable.from(values));
    }

    static <T> StyxObservable<T> from(CompletionStage<T> completableFuture) {
        return new StyxCoreObservable<>(completableFuture);
    }

    static <T> StyxObservable<T> error(Throwable error) {
        return new StyxCoreObservable<>(Observable.error(error));
    }

}
