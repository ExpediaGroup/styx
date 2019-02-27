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

/**
 * Like {@link java.util.function.Supplier} but allowed to throw checked exceptions.
 *
 * @param <T> type of supplied value
 */
public interface SupplierWithCheckedException<T> {
    T get() throws Exception;

    /**
     * Derives a Java 8+ standard library supplier from this object.
     * If this object throws an Exception, it will be wrapped in a {@link UncheckedWrapperException} before being rethrown.
     *
     * @return a standard supplier
     * @throws UncheckedWrapperException an exception for wrapping exceptions
     */
    default Supplier<T> toStandardSupplier() throws UncheckedWrapperException {
        SupplierWithCheckedException<T> original = this;

        return () -> {
            try {
                return original.get();
            } catch (Exception e) {
                throw new UncheckedWrapperException(e);
            }
        };
    }

    /**
     * Derives a supplier that will delegate to this object once for a value, then return that value every subsequent time it is called.
     * A parameter allows the caller to determine if the same applies to exceptions - if the first call throws an Exception, should
     * it rethrow that exception every time, or call the delegate again until it gets a value?
     *
     * @param recordExceptions true if a first-call exception should be rethrown on subsequent calls
     * @return a recording supplier
     */
    default SupplierWithCheckedException<T> recordFirstOutput(boolean recordExceptions) {
        return new RecordingSupplier<>(this, recordExceptions);
    }
}
