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
package com.hotels.styx.common.http.handler;

import com.google.common.net.MediaType;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * HTTP handler that responds with a static body.
 */
public class CachedBodyHttpHandler extends BaseHttpHandler {
    private final String contentType;
    private final Supplier<Body> bodySupplier;

    /**
     * Constructs a new instance with a configurable charset.
     *
     * @param builder builder
     */
    public CachedBodyHttpHandler(Builder builder) {
        this.contentType = builder.contentType.toString();

        // extracted externally because we don't want the lambda to have a reference to the builder (which is mutable).
        Supplier<String> contentSupplier = builder.contentSupplier;
        Charset charset = builder.charset;
        Supplier<Body> bodySupplier = () -> new Body(contentSupplier.get(), charset);

        this.bodySupplier = memoizeWithExpiration(bodySupplier::get, builder.expiration, builder.expirationUnit)::get;
    }

    @Override
    public HttpResponse doHandle(HttpRequest request) {
        return bodySupplier.get().toResponse();
    }

    private class Body {
        private final String content;
        private final int contentLength;

        Body(String content, Charset charset) {
            this.content = content;
            this.contentLength = content.getBytes(charset).length;
        }

        HttpResponse toResponse() {
            return FullHttpResponse.response(OK)
                    .header(CONTENT_TYPE, contentType)
                    .header(CONTENT_LENGTH, contentLength)
                    .body(content, UTF_8)
                    .build()
                    .toStreamingResponse();
        }
    }

    /**
     * A builder for CachedBodyHttpHandler class.
     */
    public static final class Builder {
        private final Supplier<String> contentSupplier;

        private MediaType contentType = PLAIN_TEXT_UTF_8;
        private Charset charset = UTF_8;
        private long expiration = 1;
        private TimeUnit expirationUnit = SECONDS;

        public Builder(Supplier<String> contentSupplier) {
            this.contentSupplier = requireNonNull(contentSupplier);
        }

        public Builder contentType(MediaType contentType) {
            this.contentType = requireNonNull(contentType);
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = requireNonNull(charset);
            return this;
        }

        public Builder expiration(long expiration) {
            this.expiration = requireNonNull(expiration);
            return this;
        }

        public Builder expirationUnit(TimeUnit expirationUnit) {
            this.expirationUnit = requireNonNull(expirationUnit);
            return this;
        }

        public CachedBodyHttpHandler build() {
            return new CachedBodyHttpHandler(this);
        }
    }
}
