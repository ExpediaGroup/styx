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
package com.hotels.styx.common;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Provides the ability to easily apply side effects and failure handling to the processing of a sequence of inputs
 * without cluttering code with nested loops, try-catch and if-else statements.
 *
 * @param <T> input type
 * @param <E> output type
 */
public class SequenceProcessor<T, E> {
    private final Flux<Attempt<T, E>> flux;
    private final Consumer<Map<T, Exception>> failuresPostProcessing;

    private SequenceProcessor(Flux<Attempt<T, E>> flux, Consumer<Map<T, Exception>> failuresPostProcessing) {
        this.flux = requireNonNull(flux);
        this.failuresPostProcessing = requireNonNull(failuresPostProcessing);
    }

    /**
     * Create a SequenceProcessor, handling a list of inputs.
     * If no mapping is added, all inputs will be returned as outputs with no failures.
     *
     * @param inputs inputs
     * @param <X>    input type
     * @return a new SequenceProcessor
     */
    public static <X> SequenceProcessor<X, X> processSequence(List<X> inputs) {
        return new SequenceProcessor<>(
                Flux.fromIterable(inputs).map(element -> new Attempt<>(element, element)),
                any -> {
                    // do nothing
                });
    }

    /**
     * Maps a function onto the SequenceProcessor so that it will be performed on every input that goes through.
     * It will run on every input even if the function throws exceptions - these can be handled later with the
     * {@link #onEachFailure(BiConsumer)} and {@link #failuresPostProcessing(Consumer)} methods.
     *
     * @param function function
     * @param <R>      output type
     * @return transformed SequenceProcessor
     */
    public <R> SequenceProcessor<T, R> map(Function<E, R> function) {
        Flux<Attempt<T, R>> mapped = this.flux.map(item -> {
            if (item.failed()) {
                return (Attempt<T, R>) item;
            }

            try {
                return new Attempt<>(item.input, function.apply(item.output));
            } catch (Exception e) {
                return new Attempt<>(item.input, e);
            }
        });

        return new SequenceProcessor<>(mapped, failuresPostProcessing);
    }

    /**
     * Adds a side-effect to each successful input and its output.
     *
     * @param consumer side-effect
     * @return transformed SequenceProcessor
     */
    public SequenceProcessor<T, E> onEachSuccess(BiConsumer<T, E> consumer) {
        return new SequenceProcessor<>(flux.doOnNext(attempt -> {
            if (attempt.success()) {
                consumer.accept(attempt.input, attempt.output);
            }
        }), failuresPostProcessing);
    }

    /**
     * Adds a side-effect to each failed input and its exception.
     * This can be used to log, collect metrics, or fail-fast by rethrowing the exception (or throwing a new exception).
     *
     * @param consumer side-effect
     * @return transformed SequenceProcessor
     */
    public SequenceProcessor<T, E> onEachFailure(BiConsumer<T, Exception> consumer) {
        return new SequenceProcessor<>(flux.doOnNext(attempt -> {
            if (attempt.failed()) {
                consumer.accept(attempt.input, attempt.exception);
            }
        }), failuresPostProcessing);
    }

    /**
     * Adds behaviour to execute on failed input and its exception, after all processing is finished.
     * It is only executed if at least one input resulted in an exception.
     * <p>
     * This is useful for when you choose not to fail-fast on individual inputs.
     * By throwing an exception in the provided lambda, you can fail instead of allowing the outputs to be collected.
     *
     * @param failuresPostProcessing behaviour to execute
     * @return transformed SequenceProcessor
     */
    public SequenceProcessor<T, E> failuresPostProcessing(Consumer<Map<T, Exception>> failuresPostProcessing) {
        return new SequenceProcessor<>(flux, failuresPostProcessing);
    }

    /**
     * Collect up all outputs from successful inputs.
     *
     * @return list of outputs
     */
    public List<E> collect() {
        List<Attempt<T, E>> attempts = flux.collectList().block();

        Map<T, Exception> failures = attempts.stream()
                .filter(Attempt::failed)
                .collect(toMap(
                        attempt -> attempt.input,
                        attempt -> attempt.exception
                ));

        if (!failures.isEmpty()) {
            failuresPostProcessing.accept(failures);
        }

        return attempts.stream().filter(Attempt::success)
                .map(attempt -> attempt.output)
                .collect(toList());
    }

    private static final class Attempt<T, E> {
        private final T input;
        private final E output;
        private final Exception exception;

        private Attempt(T input, E output) {
            this.input = requireNonNull(input);
            this.output = output;
            this.exception = null;
        }

        private Attempt(T input, Exception exception) {
            this.input = requireNonNull(input);
            this.output = null;
            this.exception = requireNonNull(exception);
        }

        private boolean failed() {
            return exception != null;
        }

        private boolean success() {
            return !failed();
        }
    }
}
