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
package com.hotels.styx.plugins;

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import com.hotels.styx.api.HttpRequest;

public class OnCompleteErrorPlugin implements Plugin {

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {

        return fromRxObservable(toRxObservable(chain.proceed(request))
                .doOnCompleted(() -> {
                    if (request.header("Fail_at_onCompleted").isPresent()) {
                        throw new RuntimeException("foobar");
                    }
                }));
    }
}
