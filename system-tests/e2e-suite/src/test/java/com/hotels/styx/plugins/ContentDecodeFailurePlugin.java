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

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;
import io.netty.buffer.ByteBuf;

import java.util.function.Function;

import static com.google.common.base.Charsets.UTF_8;
import com.hotels.styx.api.HttpRequest;

public class ContentDecodeFailurePlugin implements Plugin {
    private final int maxContentBytes;

    public ContentDecodeFailurePlugin(int maxContentBytes) {
        this.maxContentBytes = maxContentBytes;
    }

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request)
                .flatMap(response -> response.toFullResponse(maxContentBytes))
                .map(fullResponse -> fullResponse.newBuilder()
                        .header("ContentDecodeFailurePlugin", "yes")
                        .header("bytes_aggregated", fullResponse.body().length)
                        .body(fullResponse.body(), true)
                        .build())
                .map(FullHttpResponse::toStreamingResponse);
    }

    // TODO: See https://github.com/HotelsDotCom/styx/issues/202
    private Function<ByteBuf, String> decodeOrFailOperation(HttpRequest request) {
        return (byteBuf) -> {
            if (request.header("Fail_during_decoder").isPresent()) {
                throw new RuntimeException("Simulated exception during content decode");
            }
            return byteBuf.toString(UTF_8);
        };
    }
}
