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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * {@code Eventual} is a {@link Publisher} that emits at most only one event.
 *
 * Eventual is meant to expose HTTP responses to {@link HttpInterceptor}s in
 * a Styx proxy. As such {@code Eventual} only exposes operations that are
 * meaningful and safe for processing live network traffic.
 *
 * @param <T> type of object published
 */
public final class Eventual<T> implements Publisher<T> {
    private final Publisher<T> publisher;

    /**
     * Constructs a new Eventual object from an reactive streams {@link Publisher}.
     *
     * @param publisher publisher
     */
    public Eventual(Publisher<T> publisher) {
        this.publisher = publisher;
    }

    /**
     * Creates a new {@link Eventual} object from given value.
     *
     * @param value the emitted value
     * @param <T> an element type
     * @return an {@link Eventual} object
     */
    public static <T> Eventual<T> of(T value) {
        return fromMono(Mono.just(value));
    }

    /**
     * Creates a new {@link Eventual} from a {@link CompletionStage}.
     *
     * @param completionStage a {@link CompletionStage} instance
     * @param <T> an event type
     * @return an {@link Eventual} object
     */
    public static <T> Eventual<T> from(CompletionStage<T> completionStage) {
        return fromMono(Mono.fromCompletionStage(completionStage));
    }

    /**
     * Creates a new (@link Eventual} that emits an error.
     *
     * @param error a {@link Throwable} object
     * @param <T> an element type
     * @return an {@link Eventual} object
     */
    public static <T> Eventual<T> error(Throwable error) {
        return fromMono(Mono.error(error));
    }

    private static <T> Eventual<T> fromMono(Mono<T> mono) {
        return new Eventual<>(mono);
    }

    /**
     * Transforms an element synchronously by applying a mapping function.
     *
     * @param transformation a mapping function
     * @param <R> new event type
     * @return a new {@link Eventual} with mapping applied
     */
    public <R> Eventual<R> map(Function<? super T, ? extends R> transformation) {
        return fromMono(Mono.from(publisher).map(transformation));
    }

    /**
     * Transform an element asynchronously by applying a mapping function.
     *
     * @param transformation a mapping function
     * @param <R> new event type
     * @return a new {@link Eventual} with mapping applied
     */
    public <R> Eventual<R> flatMap(Function<? super T, ? extends Eventual<? extends R>> transformation) {
        return fromMono(Mono.from(publisher).flatMap(value -> Mono.from(transformation.apply(value))));
    }

    /**
     * Transforms an error by applying an error handler function.
     *
     * @param errorHandler an error handler function
     * @return a new {@link Eventual} with error handler applied
     */
    public Eventual<T> onError(Function<Throwable, ? extends Eventual<? extends T>> errorHandler) {
        return fromMono(Mono.from(publisher)
                .onErrorResume(value -> Mono.from(errorHandler.apply(value))));
    }

    /**
     * A reactive streams {@link Publisher#subscribe(Subscriber)} method.
     *
     * This method accepts any reactive-streams compatible {@link Subscriber} object, and
     * as such provides a mechanism to connect this {@link Eventual} to the other reactive-
     * stream implementation.
     *
     * @param subscriber a reactive streams compatible {@link Subscriber}
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        this.publisher.subscribe(subscriber);
    }
}

