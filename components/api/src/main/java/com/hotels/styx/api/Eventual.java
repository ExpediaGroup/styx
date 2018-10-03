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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Exposes a transformation API for HTTP interceptors.
 * <p>
 * This interface provides a is *not* intended for plugins to extend.
 *
 * @param <T> type of object published
 */
public final class Eventual<T> implements Publisher<T> {
    private final Publisher<T> publisher;

    public Eventual(Publisher<T> publisher) {
        this.publisher = publisher;
    }

    private static <T> Eventual<T> fromMono(Mono<T> mono) {
        return new Eventual<>(mono);
    }

    public <R> Eventual<R> map(Function<? super T, ? extends R> transformation) {
        return fromMono(Mono.from(publisher).map(transformation));
    }

    public <R> Eventual<R> flatMap(Function<? super T, ? extends Eventual<? extends R>> transformation) {
        return fromMono(Mono.from(publisher).flatMap(value -> Mono.from(transformation.apply(value))));
    }

    public Eventual<T> onError(Function<Throwable, ? extends Eventual<? extends T>> errorHandler) {
        return fromMono(Mono.from(publisher).onErrorResume(value -> Mono.from(errorHandler.apply(value))));
    }

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
    public CompletableFuture<T> asCompletableFuture() {
        return Mono.from(publisher).toFuture();
    }

    // Static Factory Methods

    public static <T> Eventual<T> of(T value) {
        return fromMono(Mono.just(value));
    }

    public static <T> Eventual<T> from(CompletionStage<T> completableFuture) {
        return fromMono(Mono.fromCompletionStage(completableFuture));
    }

    public static <T> Eventual<T> error(Throwable error) {
        return fromMono(Mono.error(error));
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        this.publisher.subscribe(subscriber);
    }
}

