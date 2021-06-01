/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import io.micrometer.core.instrument.Metrics;
import reactor.core.publisher.Flux;

public class OnCompleteErrorPlugin implements Plugin {
    private String token;

    OnCompleteErrorPlugin() {
        this.token = "";
    }

    OnCompleteErrorPlugin(String token) {
        this.token = token;
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {

        return new Eventual<>(Flux.from(chain.proceed(request))
                .doOnComplete(() -> {
                    if (request.header("Fail_at_onCompleted").isPresent()) {
                        Metrics.counter("plugins.failAtOnCompletedPlugin" + token + ".errors").increment();
                        throw new RuntimeException("foobar");
                    }
                }));
    }
}
