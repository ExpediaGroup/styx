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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpResponse;
import rx.Observable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.currentThread;
import static rx.RxReactiveStreams.toObservable;

public final class BlockingObservables {

    public static <T> T getFirst(Observable<T> observable) {
        return observable.toBlocking().single();
    }

    public static <T> T getFirst(Eventual<T> observable) {
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

    public static HttpResponse waitForResponse(Eventual<LiveHttpResponse> responseObs) {
        return futureGetAndPropagate(responseObs
                .flatMap(response -> response.toFullResponse(120*1024))
                .asCompletableFuture());
    }

    public static HttpResponse waitForResponse(Observable<LiveHttpResponse> responseObs) {
        return responseObs
                .flatMap(response -> toObservable(response.toFullResponse(120*1024)))
                .toBlocking()
                .single();
    }

    public static LiveHttpResponse waitForStreamingResponse(Observable<LiveHttpResponse> responseObs) {
        return responseObs
                .toBlocking()
                .first();
    }

    private BlockingObservables() {
    }
}
