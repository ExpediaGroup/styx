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
package com.hotels.styx.support.api;

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import rx.Observable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static java.lang.Thread.currentThread;

public final class BlockingObservables {

    public static <T> T getFirst(Observable<T> observable) {
        return observable.toBlocking().single();
    }

    public static <T> T getFirst(StyxObservable<T> observable) {
        return futureGetAndPropagate(observable.asCompletableFuture());
    }

    private static <T> T futureGetAndPropagate(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    public static FullHttpResponse waitForResponse(StyxObservable<HttpResponse> responseObs) {
        return futureGetAndPropagate(responseObs
                .flatMap(response -> response.toFullResponse(120*1024))
                .asCompletableFuture());
    }

    public static FullHttpResponse waitForResponse(Observable<HttpResponse> responseObs) {
        return responseObs
                .flatMap(response -> toRxObservable(response.toFullResponse(120*1024)))
                .toBlocking()
                .single();
    }

    public static HttpResponse waitForStreamingResponse(Observable<HttpResponse> responseObs) {
        return responseObs
                .toBlocking()
                .first();
    }

    private BlockingObservables() {
    }
}
