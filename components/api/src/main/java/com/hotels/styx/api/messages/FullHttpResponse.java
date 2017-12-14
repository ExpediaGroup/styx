/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.api.messages;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.messages.HttpSupport.encodeBody;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * HTTP response with a fully aggregated/decoded body.
 */
public class FullHttpResponse implements FullHttpMessage {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final String body;
    private final List<HttpCookie> cookies;

    FullHttpResponse(Builder builder) {
        this.version = builder.version;
        this.status = builder.status;
        this.headers = builder.headers.build();
        this.body = builder.body;
        this.cookies = ImmutableList.copyOf(builder.cookies);
    }

    /**
     * Creates an HTTP response builder with a status of 200 OK and empty body.
     *
     * @return a new builder
     */
    public static Builder response() {
        return new Builder();
    }

    /**
     * Creates an HTTP response builder with a given status and empty body.
     *
     * @param status response status
     * @return a new builder
     */
    public static Builder response(HttpResponseStatus status) {
        return new Builder(status);
    }

    @Override
    public Optional<String> header(CharSequence name) {
        return headers.get(name);
    }

    @Override
    public List<String> headers(CharSequence name) {
        return headers.getAll(name);
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public List<HttpCookie> cookies() {
        return cookies;
    }

    @Override
    public String body() {
        return body;
    }

    @Override
    public HttpVersion version() {
        return version;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    public HttpResponseStatus status() {
        return status;
    }

    public boolean isRedirect() {
        return status().code() >= 300 && status().code() < 400;
    }

    /**
     * Encodes this response into a streaming form, using the provided encoder to transform the body from an arbitrary type
     * to a buffer of bytes.
     *
     * @param encoder an encoding function
     * @return an encoded (streaming) response
     */
    public HttpResponse toStreamingHttpResponse(Function<String, ByteBuf> encoder) {
        return new HttpResponse.Builder(this, encodeBody(this.body, encoder))
                .build();
    }

    /**
     * Encodes a response with a body of type String into a streaming form, using a UTF-8 encoding.
     *
     * @param response a response
     * @return an encoded (streaming) response
     */
    public static HttpResponse toStreamingHttpResponse(FullHttpResponse response) {
        return response.toStreamingHttpResponse(string -> Unpooled.copiedBuffer(string, UTF_8));
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("version", version)
                .add("status", status)
                .add("headers", headers)
                .add("cookies", cookies)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version, status, headers, cookies);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FullHttpResponse other = (FullHttpResponse) obj;
        return Objects.equal(this.version, other.version)
                && Objects.equal(this.status, other.status)
                && Objects.equal(this.headers, other.headers)
                && Objects.equal(this.cookies, other.cookies);
    }

    /**
     * Builder.
     */
    public static final class Builder {
        private HttpResponseStatus status = OK;
        private HttpHeaders.Builder headers;
        private HttpVersion version = HTTP_1_1;
        private boolean validate = true;
        private String body;
        private final List<HttpCookie> cookies;

        public Builder() {
            this.headers = new HttpHeaders.Builder();
            this.body = null;
            this.cookies = new ArrayList<>();
        }

        public Builder(HttpResponseStatus status) {
            this();
            this.status = status;
        }

        public Builder(FullHttpResponse response) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = response.body();
            this.cookies = new ArrayList<>(response.cookies());
        }

        public Builder(HttpResponse response, String decoded) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = decoded;
            this.cookies = new ArrayList<>(response.cookies());
        }

        /**
         * Sets the response status.
         *
         * @param status response status
         * @return {@code this}
         */
        public Builder status(HttpResponseStatus status) {
            this.status = requireNonNull(status);
            return this;
        }

        /**
         * Sets the response body.
         *
         * @param content response body
         * @return {@code this}
         */
        public Builder body(String content) {
            String sanitisedContent = content == null ? "" : content;

            header(CONTENT_LENGTH, sanitisedContent.getBytes(UTF_8).length);
            this.body = sanitisedContent;
            return this;
        }

        /**
         * Sets the HTTP version.
         *
         * @param version HTTP version
         * @return {@code this}
         */
        public Builder version(HttpVersion version) {
            this.version = requireNonNull(version);
            return this;
        }

        /**
         * Sets the value of the {@code Content-Type} header.
         * @param contentType content type
         *
         * @return {@code this}
         */
        public Builder contentType(MediaType contentType) {
            headers.set(CONTENT_TYPE, contentType.toString());
            return this;
        }

        /**
         * Disables client-side caching of this response.
         *
         * @return {@code this}
         */
        public Builder disableCaching() {
            header("Pragma", "no-cache");
            header("Expires", "Mon, 1 Jan 2007 08:00:00 GMT");
            header("Cache-Control", "no-cache,must-revalidate,no-store");
            return this;
        }

        /**
         * Makes this response chunked.
         *
         * @return {@code this}
         */
        public Builder setChunked() {
            headers.add(TRANSFER_ENCODING, CHUNKED);
            headers.remove(CONTENT_LENGTH);
            return this;
        }

        /**
         * Adds a response cookie (adds a new Set-Cookie header).
         *
         * @param cookie cookie to add
         * @return {@code this}
         */
        public Builder addCookie(HttpCookie cookie) {
            cookies.add(checkNotNull(cookie));
            return this;
        }

        /**
         * Adds a response cookie (adds a new Set-Cookie header).
         *
         * @param name  cookie name
         * @param value cookie value
         * @return {@code this}
         */
        public Builder addCookie(String name, String value) {
            return addCookie(HttpCookie.cookie(name, value));
        }

        /**
         * Removes a cookie if present (removes its Set-Cookie header).
         *
         * @param name name of the cookie
         * @return {@code this}
         */
        public Builder removeCookie(String name) {
            cookies.stream()
                    .filter(cookie -> cookie.name().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(cookies::remove);

            return this;
        }

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return {@code this}
         */
        public Builder header(CharSequence name, Object value) {
            this.headers.set(name, value);
            return this;
        }

        /**
         * Adds a new header with the specified {@code name} and {@code value}.
         * <p/>
         * Will not replace any existing values for the header.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return {@code this}
         */
        public Builder addHeader(CharSequence name, Object value) {
            headers.add(name, value);
            return this;
        }

        /**
         * Removes the header with the specified name.
         *
         * @param name The name of the header to remove
         * @return {@code this}
         */
        public Builder removeHeader(CharSequence name) {
            headers.remove(name);
            return this;
        }

        /**
         * Sets the headers.
         *
         * @param headers headers
         * @return {@code this}
         */
        public Builder headers(HttpHeaders headers) {
            this.headers = headers.newBuilder();
            return this;
        }

        /**
         * Enable validation of uri and some headers.
         *
         * @return {@code this}
         */
        public Builder disableValidation() {
            this.validate = false;
            return this;
        }

        /**
         * Builds a new full response based on the settings configured in this builder.
         * If {@code validate} is set to true:
         * <ul>
         * <li>an exception will be thrown if the content length is not an integer, or more than one content length exists</li>
         * </ul>
         *
         * @return a new full response
         */
        public FullHttpResponse build() {
            if (validate) {
                ensureContentLengthIsValid();
            }

            return new FullHttpResponse(this);
        }

        Builder ensureContentLengthIsValid() {
            List<String> contentLengths = headers.build().getAll(CONTENT_LENGTH);

            checkArgument(contentLengths.size() <= 1, "Duplicate Content-Length found. %s", contentLengths);

            if (contentLengths.size() == 1) {
                checkArgument(isInteger(contentLengths.get(0)), "Invalid Content-Length found. %s", contentLengths.get(0));
            }
            return this;
        }

        private static boolean isInteger(String contentLength) {
            try {
                parseInt(contentLength);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
