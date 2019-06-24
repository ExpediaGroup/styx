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

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Provides lazy-initialisation feature with convenient syntax.
 *
 * @param <E> type of value supplied
 */
public class Lazy<E> implements Supplier<E> {
    private final LazyInitialisation<E> lazy;

    private Lazy(Supplier<E> supplier) {
        this.lazy = new LazyInitialisation<>(supplier);
    }

    /**
     * Get a lazy instance.
     *
     * The given Supplier will be called the first time this {@link Lazy} is used.
     * The resulting value will then be reused on all subsequent calls.
     *
     * @param supplier supplier
     * @param <E> type
     * @return lazy supplier
     */
    public static <E> Lazy<E> lazy(Supplier<E> supplier) {
        return new Lazy<>(requireNonNull(supplier));
    }

    @Override
    public E get() {
        return lazy.getLazyValue();
    }
}
