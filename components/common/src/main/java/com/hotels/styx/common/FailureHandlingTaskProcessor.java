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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Allows various types of failure handling to be applied to a task running over a list of inputs.
 *
 * It can be as strict or lenient as desired - from failing the whole run immediately as soon as a single iteration fails,
 * to just logging failures and returning successful results.
 *
 * The behaviour is configured by injecting lambdas when an instance of this class is built using the {@link Builder}.
 *
 * @param <T> input type
 * @param <R> output type
 */
public class FailureHandlingTaskProcessor<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandlingTaskProcessor.class);

    private final BiConsumer<T, Exception> onEachFailure;
    private final Consumer<Map<T, Exception>> failuresPostProcessing;

    private FailureHandlingTaskProcessor(Builder<T, R> builder) {
        this.onEachFailure = builder.onEachFailure;
        this.failuresPostProcessing = builder.failuresPostProcessing;
    }

    /**
     * Execute the task on a list of inputs, using the configured failure handling.
     *
     * @param inputs a list of inputs
     * @param task a task to execute
     * @return a list of outputsl
     */
    public List<R> process(List<T> inputs, Task<T, R> task) {
        List<R> successes = new ArrayList<>();
        Map<T, Exception> failures = new LinkedHashMap<>();

        inputs.forEach(input -> {
            try {
                successes.add(task.execute(input));
            } catch (Exception t) {
                onEachFailure.accept(input, t);
                failures.put(input, t);
            }
        });

        if (!failures.isEmpty()) {
            failuresPostProcessing.accept(failures);
        }

        return successes;
    }

    /**
     * Builds {@link FailureHandlingTaskProcessor}.
     *
     * @param <T> input type
     * @param <R> output type
     */
    public static final class Builder<T, R> {
        // Logs by default so that failures do not become invisible.
        private BiConsumer<T, Exception> onEachFailure = (input, err) -> LOGGER.error("Failed on input " + input, err);
        private Consumer<Map<T, Exception>> failuresPostProcessing = failures -> LOGGER.error("Failures: " + failures);

        /**
         * An action to take immediately after a failure. This can be used to fail fast by rethrowing the error,
         * or to log the failure as soon as it happens without waiting.
         *
         * @param onEachFailure an action to take
         * @return this builder
         */
        public Builder<T, R> doImmediatelyOnEachFailure(BiConsumer<T, Exception> onEachFailure) {
            this.onEachFailure = requireNonNull(onEachFailure);
            return this;
        }

        /**
         * An action to take after processing all list items, if there were failures.
         * This will only execute if the individual failures were did not rethrow their exceptions.
         *
         * This action may be used to throw an exception, otherwise the {@link #process(List, Task)} method
         * will return whatever successful results it had (or an empty list if none).
         *
         * @param failuresPostProcessing an action to take
         * @return this builder
         */
        public Builder<T, R> doOnFailuresAfterAllProcessing(Consumer<Map<T, Exception>> failuresPostProcessing) {
            this.failuresPostProcessing = requireNonNull(failuresPostProcessing);
            return this;
        }

        /**
         * Build an instance of {@link FailureHandlingTaskProcessor} using the configuration provided.
         *
         * @return a new instance
         */
        public FailureHandlingTaskProcessor<T, R> build() {
            return new FailureHandlingTaskProcessor<>(this);
        }
    }

    /**
     * A task to execute. Similar to {@link java.util.function.Function}, except it is allowed to throw any type of
     * {@link Exception}.
     *
     * @param <T> input type
     * @param <R> output type
     */
    public interface Task<T, R> {
        /**
         * Execute task.
         *
         * @param input input
         * @return output
         * @throws Exception if there is a failure
         */
        R execute(T input) throws Exception;
    }
}
