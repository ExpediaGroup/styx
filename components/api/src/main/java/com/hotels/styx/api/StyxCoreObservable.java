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
 * An observable that underpins the StyxObservable interface.
 *
 * @param <T> type of object published
 */
class StyxCoreObservable<T> implements StyxObservable<T> {
    private final Observable<T> delegate;

    public StyxCoreObservable(Observable<T> delegate) {
        this.delegate = delegate;
    }

    public StyxCoreObservable(CompletionStage<T> future) {
        this.delegate = toObservable(future);
    }

    public static <T> StyxObservable<T> empty() {
        return new StyxCoreObservable<>(Observable.empty());
    }

    public static <T> StyxObservable<T> of(T item) {
        return new StyxCoreObservable<T>(Observable.just(item));
    }

    public static <T> StyxObservable<T> error(Throwable cause) {
        return new StyxCoreObservable<T>(Observable.error(cause));
    }

    public <R> StyxObservable<R> map(Function<? super T, ? extends R> transformation) {
        return new StyxCoreObservable<>(delegate.map(transformation::apply));
    }

    public <R> StyxObservable<R> flatMap(Function<? super T, ? extends StyxObservable<? extends R>> transformation) {
        return new StyxCoreObservable<>(delegate.flatMap(response ->
                toObservable(transformation.apply(response))));
    }

    public <R> StyxObservable<R> reduce(BiFunction<? super T, R, R> accumulator, R seed) {
        return new StyxCoreObservable<>(delegate.reduce(seed, (result, element) -> accumulator.apply(element, result)));
    }

    @Override
    public StyxObservable<T> onError(Function<Throwable, ? extends StyxObservable<? extends T>> errorHandler) {
        return new StyxCoreObservable<>(delegate.onErrorResumeNext(cause ->
                toObservable(errorHandler.apply(cause))));
    }

    public Observable<T> delegate() {
        return delegate;
    }

    public CompletableFuture<T> asCompletableFuture() {
        return fromSingleObservable(delegate);
    }

    private static <T> CompletableFuture<T> fromSingleObservable(Observable<T> observable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        observable.single().subscribe(future::complete, future::completeExceptionally);
        return future;
    }

    private static <T> Observable<T> toObservable(StyxObservable<T> styxObservable) {
        return styxObservable instanceof StyxCoreObservable
                ? ((StyxCoreObservable<T>) styxObservable).delegate
                : toObservable(styxObservable.asCompletableFuture());
    }

    private static <T> Observable<T> toObservable(CompletionStage<T> future) {
        return Observable.create(subscriber ->
                future.whenComplete((result, error) -> {
                    if (error != null) {
                        subscriber.onError(error);
                    } else {
                        subscriber.onNext(result);
                        subscriber.onCompleted();
                    }
                }));
    }
}
