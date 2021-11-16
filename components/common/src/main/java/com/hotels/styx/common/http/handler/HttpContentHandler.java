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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;

import java.nio.charset.Charset;
import java.util.function.Supplier;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.util.Objects.requireNonNull;

/**
 * Responds with UTF8 text stream.
 */
public class HttpContentHandler implements HttpHandler {

    private final Supplier<String> content;
    private final CharSequence contentType;
    private final Charset encoding;

    public HttpContentHandler(CharSequence contentType, Charset encoding, Supplier<String> content) {
        this.content = requireNonNull(content, "content supplier cannot be null");
        this.contentType = requireNonNull(contentType, "contentType cannot be null");
        this.encoding = requireNonNull(encoding, "encoding cannot be null");
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(createResponse());
    }

    private LiveHttpResponse createResponse() {
        return response(OK)
                .disableCaching()
                .addHeader(CONTENT_TYPE, contentType)
                .body(content.get(), encoding)
                .build()
                .stream();
    }
}
