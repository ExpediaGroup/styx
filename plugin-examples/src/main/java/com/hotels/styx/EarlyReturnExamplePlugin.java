/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This shows an example of a plugin that responds immediately to a received request, instead of proxying it downstream.
 * It takes a request and if the HTTP request contains a header named `X-Respond`, then it returns an eventual wrapping
 * the HTTP response
 * The eventual object is returned immediately, even if the response has not yet arrived.
 */
public class EarlyReturnExamplePlugin implements Plugin {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        if (request.header("X-Respond").isPresent()) {
            return request.consume(1024).map(anyRequest ->
                    HttpResponse.response(OK)
                            .header(CONTENT_TYPE, "text/plain; charset=utf-8")
                            .body("Responding from plugin", UTF_8)
                            .build()
                            .stream());
        } else {
            return chain.proceed(request);
        }
    }
}

