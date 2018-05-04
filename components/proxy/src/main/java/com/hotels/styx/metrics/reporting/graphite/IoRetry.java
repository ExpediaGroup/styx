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
package com.hotels.styx.metrics.reporting.graphite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import static java.lang.String.format;

/**
 * Generic retry class for tasks that perform I/O.
 */
final class IoRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphiteReporter.class);

    /**
     * Executes the provided {@code task} retrying up to {@code times} times if an {@code IOException} is thrown.
     * <p>
     * This method executes a {@code task} which can throw an {@code IOException}. If the exception is thrown,
     * the {@code onError} block will be executed and the {@code task} retried up to {@code times} times. If the
     * limit of retries is reached, the method will return an {@code UncheckedIOException} encapsulating the {@code IOException}.
     *
     * @param times        positive integer defining the number of times the {@code task} will be retried.
     * @param task         block that can throw an {@code IOException}
     * @param errorHandler block to be executed whan an {@code IOException} occurs.
     * @throws UncheckedIOException     wrapper around the original {@code IOException} thrown by {@code task}
     *                                  when the limit of retries is reached.
     * @throws IllegalArgumentException if {@code times} is less than 1.
     */
    public static void tryTimes(int times, IOAction task, Consumer<IOException> errorHandler)
            throws UncheckedIOException, IllegalArgumentException {

        if (times < 1) {
            throw new IllegalArgumentException("The number of retries should be a positive integer. It was " + times);
        }
        int retries = 0;

        while (true) {
            try {
                task.run();
                return;
            } catch (IOException e) {
                onError(errorHandler, e);
                if (++retries == times) {
                    throw new UncheckedIOException(
                            format("Operation failed after %d retries: %s", times, e.getMessage()),
                            e);
                }
            }
        }
    }

    private static void onError(Consumer<IOException> consumer, IOException failure) {
        try {
            consumer.accept(failure);
        } catch (Exception e) {
            LOGGER.warn("OnError block for I/O operation failed: " + e.getLocalizedMessage(), e);
        }
    }

    private IoRetry() {
    }

}