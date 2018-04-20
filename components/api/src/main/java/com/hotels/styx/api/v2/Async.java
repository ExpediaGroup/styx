/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.api.v2;

import java.util.concurrent.CompletionStage;

/**
 * Provider for asynchronous operations in styx Interceptor context.
 */
public interface Async {
    /**
     * Returns a new StyxObservable instance.
     *
     * @param <T> Item to be emitted via styx observable.
     * @return A Styx observable returning an item.
     */
    <T> StyxObservable<T> just(T item);

    /**
     * Returns a new StyxObservable instance that always returns an error.
     *
     * @param <T> Item to be emitted via styx observable.
     * @return A Styx observable returning an item.
     */
    <T> StyxObservable<T> error(Throwable item);

    /**
     * Converts a Java CompletionStage to a styx observable.
     *
     * @param <T> Item to be emitted via styx observable.
     * @return A Styx observable returning an item.
     */
    <T> StyxObservable<T> fromCompletionStage(CompletionStage<T> stage);

}
