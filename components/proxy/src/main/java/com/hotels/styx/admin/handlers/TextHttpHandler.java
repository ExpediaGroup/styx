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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;

import java.util.function.Supplier;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Responds with UTF8 text stream.
 */
public class TextHttpHandler implements HttpHandler {

    private final Supplier<String> text;

    public TextHttpHandler(Supplier<String> text) {
        this.text = requireNonNull(text, "text supplier cannot be null");
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(createResponse());
    }

    protected LiveHttpResponse createResponse() {
        return response(OK)
                .disableCaching()
                .addHeader(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(ByteStream.from(text.get(), UTF_8))
                .build();
    }
}
