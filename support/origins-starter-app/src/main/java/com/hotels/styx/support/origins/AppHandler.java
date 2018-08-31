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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler;
import com.hotels.styx.api.HttpResponseStatus;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.fill;
import static java.util.UUID.randomUUID;

public class AppHandler implements HttpHandler {
    private final HttpHandler handler;
    private final Origin origin;

    public AppHandler(Origin origin) {
        this.origin = origin;
        this.handler = new StaticBodyHttpHandler(HTML_UTF_8, makeAResponse(origin));
    }

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return handler.handle(request, context)
                .map(response -> {
                    HttpResponse.Builder responseBuilder = response.newBuilder()
                            .headers(request.headers())
                            .header(STUB_ORIGIN_INFO, origin.applicationInfo());

                    response.contentLength().ifPresent(contentLength -> responseBuilder.header(CONTENT_LENGTH, contentLength));
                    response.contentType().ifPresent(contentType -> responseBuilder.header(CONTENT_TYPE, contentType));

                    request.queryParam("status").ifPresent(status ->
                            responseBuilder
                                    .status(httpResponseStatus(status))
                                    .body(StyxObservable.of("Returning requested status (" + status + ")"), UTF_8)
                    );

                    request.queryParam("length").ifPresent(length ->
                            responseBuilder.body(StyxObservable.of(generateContent(parseInt(length))), UTF_8)
                    );

                    return responseBuilder.build();
                });
    }

    private static String makeAResponse(Origin origin) {
        return format("Response From %s - %s", origin.host(), randomUUID().toString());
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
