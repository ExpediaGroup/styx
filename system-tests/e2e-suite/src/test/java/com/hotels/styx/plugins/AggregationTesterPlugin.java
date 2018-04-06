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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import rx.Observable;

import static com.google.common.base.Charsets.UTF_8;

public class AggregationTesterPlugin implements Plugin {
    private final int maxContentBytes;

    public AggregationTesterPlugin(int maxContentBytes) {
        this.maxContentBytes = maxContentBytes;
    }

    @Override
    public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request)
                .flatMap(response -> response.decode((byteBuf) -> byteBuf.toString(UTF_8), maxContentBytes))
                .map(decodedResponse -> decodedResponse.responseBuilder()
                        .header("test_plugin", "yes")
                        .header("bytes_aggregated", decodedResponse.body().getBytes(UTF_8).length)
                        .body(decodedResponse.body())
                        .build());
    }
}
