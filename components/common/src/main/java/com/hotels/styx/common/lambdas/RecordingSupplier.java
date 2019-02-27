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
package com.hotels.styx.common.lambdas;

import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoize;
import static java.util.Objects.requireNonNull;

/**
 * A supplier that makes its first request to a delegate and then records the result.
 */
class RecordingSupplier<T> implements SupplierWithCheckedException<T> {
    private final Supplier<Outcome<T>> memoizer;

    RecordingSupplier(SupplierWithCheckedException<T> supplier, boolean recordExceptions) {
        Supplier<Outcome<T>> wrapped = () -> {
            try {
                return new Outcome<>(supplier.get());
            } catch (Exception e) {
                if (recordExceptions) {
                    return new Outcome<>(e);
                }

                throw new UncheckedWrapperException(e);
            }
        };

        this.memoizer = memoize(wrapped::get)::get;
    }

    @Override
    public T get() throws Exception {
        try {
            Outcome<T> outcome = memoizer.get();

            if (outcome.failed()) {
                throw outcome.exception;
            }

            return outcome.output;
        } catch (UncheckedWrapperException e) {
            throw e.wrapped();
        }
    }

    private static final class Outcome<E> {
        private final E output;
        private final Exception exception;

        private Outcome(E output) {
            this.output = output;
            this.exception = null;
        }

        private Outcome(Exception exception) {
            this.output = null;
            this.exception = requireNonNull(exception);
        }

        private boolean failed() {
            return exception != null;
        }
    }
}
