/*
  Copyright (C) 2013-2023 Expedia Inc.

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
import reactor.core.publisher.Mono;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class DelayPlugin implements Plugin {
    private final Duration requestProcessingDelay;
    private final Duration responseProcessingDelay;

    public DelayPlugin(Duration requestProcessingDelay, Duration responseProcessingDelay) {
        this.requestProcessingDelay = requireNonNull(requestProcessingDelay);
        this.responseProcessingDelay = requireNonNull(responseProcessingDelay);
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return delayedEventual(requestProcessingDelay).flatMap(n ->
                chain.proceed(request).flatMap(response ->
                        delayedEventual(responseProcessingDelay).map(i -> response)
                )
        );
    }

    private Eventual<Long> delayedEventual(Duration duration) {
        return new Eventual<>(Mono.delay(duration));
    }
}
