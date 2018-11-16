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
import reactor.core.publisher.Mono;
import rx.Observable;

import static rx.RxReactiveStreams.toObservable;


// TODO: This class needs to be removed once we have migrated over to Reactor/Flux.

public final class BlockingObservables {

    public static <T> T getFirst(Observable<T> observable) {
        return observable.toBlocking().single();
    }

    public static HttpResponse waitForResponse(Eventual<LiveHttpResponse> responseObs) {
        return Mono.from(responseObs.flatMap(response -> response.aggregate(120*1024))).block();
    }

    public static HttpResponse waitForResponse(Observable<LiveHttpResponse> responseObs) {
        return responseObs
                .flatMap(response -> toObservable(response.aggregate(120*1024)))
                .toBlocking()
                .single();
    }

    private BlockingObservables() {
    }
}
