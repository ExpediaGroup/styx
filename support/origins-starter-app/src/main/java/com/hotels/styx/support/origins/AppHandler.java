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
package com.hotels.styx.support.origins;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;

import java.util.Optional;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.fill;
import static java.util.UUID.randomUUID;

public class AppHandler implements HttpHandler {
    private final Origin origin;
    private final HttpResponse standardResponse;

    public AppHandler(Origin origin) {
        this.origin = origin;
        this.standardResponse = HttpResponse.response(OK)
                .header(CONTENT_TYPE, HTML_UTF_8.toString())
                .body(makeAResponse(origin), UTF_8)
                .build();
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        HttpResponse.Builder responseBuilder = standardResponse.newBuilder()
                .headers(request.headers())
                .header(STUB_ORIGIN_INFO, origin.applicationInfo());

        return Eventual.of(Optional.ofNullable(responseBuilder)
                .map(it -> request.queryParam("status")
                        .map(status -> it.status(httpResponseStatus(status))
                                .body("Returning requested status (" + status + ")", UTF_8))
                        .orElse(it))
                .map(it -> request.queryParam("length")
                        .map(length -> it.body(generateContent(parseInt(length)), UTF_8))
                        .orElse(it))
                .orElse(responseBuilder)
                .build()
                .stream());
    }

    private static String makeAResponse(Origin origin) {
        return format("Response From %s - %s", origin.hostAndPortString(), randomUUID().toString());
    }

    private HttpResponseStatus httpResponseStatus(String status) {
        return HttpResponseStatus.statusWithCode(Integer.valueOf(status));
    }

    private String generateContent(int contentLength) {
        char[] characters = new char[contentLength];
        fill(characters, 'c');
        return new String(characters);
    }
}
