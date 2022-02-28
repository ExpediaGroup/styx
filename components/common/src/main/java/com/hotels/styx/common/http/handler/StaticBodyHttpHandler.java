/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

import java.nio.charset.Charset;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * HTTP handler that responds with a static body.
 */
public class StaticBodyHttpHandler extends BaseHttpHandler {
    private final CharSequence contentType;
    private final String body;
    private final int contentLength;

    /**
     * Constructs a new instance, using a default charset of UTF-8.
     *
     * @param contentType Content-Type header value
     * @param body        body to return
     */
    public StaticBodyHttpHandler(CharSequence contentType, String body) {
        this(contentType, body, UTF_8);
    }

    /**
     * Constructs a new instance with a configurable charset.
     *
     * @param contentType Content-Type header value
     * @param body        body to return
     * @param charset     character set
     */
    public StaticBodyHttpHandler(CharSequence contentType, String body, Charset charset) {
        this.contentType = requireNonNull(contentType);
        this.body = requireNonNull(body);
        this.contentLength = body.getBytes(charset).length;
    }

    @Override
    public HttpResponse doHandle(HttpRequest request, HttpInterceptor.Context context) {
        return HttpResponse.response(OK)
                .header(CONTENT_TYPE, this.contentType.toString())
                .header(CONTENT_LENGTH, this.contentLength)
                .body(this.body, UTF_8)
                .build();
    }
}
