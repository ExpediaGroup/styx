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
package com.hotels.styx.common;

import com.hotels.styx.api.v2.StyxCoreObservable;
import com.hotels.styx.api.v2.StyxObservable;
import rx.Observable;

import java.util.concurrent.CompletableFuture;

/**
 * Helpers for working with CompletableFuture objects.
 */
public final class CompletableFutures {
    private CompletableFutures() {
    }

    // TODO: We need to substitute this in favour of Styx APIs
    //
    // Credit: http://www.nurkiewicz.com/2014/11/converting-between-completablefuture.html
    public static <T> CompletableFuture<T> fromSingleObservable(Observable<T> observable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        observable
                .doOnError(future::completeExceptionally)
                .single()
                .forEach(future::complete);
        return future;
    }

    public static <T> Observable<T> toRxObservable(StyxObservable<T> observable) {
        return ((StyxCoreObservable<T>) observable).delegate();
    }

}
