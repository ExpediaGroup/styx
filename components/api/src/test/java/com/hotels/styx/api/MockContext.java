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

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MockContext implements HttpInterceptor.Context {

    public static final HttpInterceptor.Context MOCK_CONTEXT = new MockContext();

    @Override
    public void add(String key, Object value) {

    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return null;
    }

    // TODO: Mikko: Styx 2.0 API: MockObservable support for `onError`.
    static class MockObservable<T> implements StyxObservable<T> {
        private final T value;

        <U> MockObservable(T value) {
            this.value = value;
        }

        @Override
        public <U> StyxObservable<U> map(Function<T, U> transformation) {
            return new MockObservable<>(transformation.apply(value));
        }

        @Override
        public <U> StyxObservable<U> flatMap(Function<T, StyxObservable<U>> transformation) {
            return transformation.apply(value);
        }

        @Override
        public <U> StyxObservable<U> reduce(BiFunction<T, U, U> accumulator, U initialValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyxObservable<T> onError(Function<Throwable, StyxObservable<T>> errorHandler) {
            return new MockObservable<>(value);
        }

        @Override
        public CompletableFuture<T> asCompletableFuture() {
            return CompletableFuture.completedFuture(this.value);
        }

        public T value() {
            return value;
        }
    }
}
