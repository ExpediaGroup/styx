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

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import static java.lang.String.format;

class IoRetry {

    @FunctionalInterface
    public interface IoRunnable {
        void run() throws IOException;
    }

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GraphiteReporter.class);

    public static void tryTimes(int times, IoRunnable task, Consumer<Exception> onError)
            throws UncheckedIOException {
        int retries = 0;

        while (true) {
            try {
                task.run();
                return;
            } catch (IOException e) {
                try {
                    onError.accept(e);
                } catch (Exception onErrorException) {
                    LOGGER.warn("OnError block for I/O operation failed: " + e.getLocalizedMessage(), e);
                }
                if (++retries == times) {
                    throw new UncheckedIOException(
                            format("Operation failed after %d retries: %s", times, e.getMessage()),
                            e);
                }
            }
        }
    }

}