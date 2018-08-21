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
package com.hotels.styx.api;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.ResponseCookie.decode;
import static com.hotels.styx.api.ResponseCookie.encode;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * HTTP response with a fully aggregated/decoded body.
 */
public class FullHttpResponse implements FullHttpMessage {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final byte[] body;

    FullHttpResponse(Builder builder) {
        this.version = builder.version;
        this.status = builder.status;
        this.headers = builder.headers.build();
        this.body = requireNonNull(builder.body);
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

    /**
     * Returns the body of this message in its unencoded form.
     *
     * @return the body
     */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * Returns the message body as a String decoded with provided character set.
     * <p>
     * Decodes the message body into a Java String object with a provided charset.
     * The caller must ensure the provided charset is compatible with message content
     * type and encoding.
     *
     * @param charset Charset used to decode message body.
     * @return Message body as a String.
     */
    @Override
    public String bodyAs(Charset charset) {
        // CHECKSTYLE:OFF
        return new String(body, charset);
        // CHECKSTYLE:ON
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
        return status.code() >= 300 && status.code() < 400;
    }

    /**
     * Converts this response to a streaming form (HttpResponse).
     * <p>
     * Converts this response to a HttpResponse object which represents the HTTP response as a
     * stream of bytes.
     *
     * @return A streaming HttpResponse object.
     */
    public HttpResponse toStreamingResponse() {
        if (this.body.length == 0) {
            return new HttpResponse.Builder(this, new StyxCoreObservable<>(Observable.empty())).build();
        } else {
            return new HttpResponse.Builder(this, StyxObservable.of(copiedBuffer(this.body))).build();
        }
    }

    /**
     * Decodes the "Set-Cookie" headers in this response and returns the cookies.
     *
     * @return cookies
     */
    public Set<ResponseCookie> cookies() {
        return decode(headers.getAll(SET_COOKIE));
    }

    /**
     * Decodes the "Set-Cookie" headers in this response and returns the specified cookie.
     *
     * @param name cookie name
     * @return cookie
     */
    public Optional<ResponseCookie> cookie(String name) {
        return cookies().stream()
                .filter(cookie -> cookie.name().equals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("version", version)
                .add("status", status)
                .add("headers", headers)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version, status, headers);
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
                && Objects.equal(this.headers, other.headers);
    }

    /**
     * Builder.
     */
    public static final class Builder {
        private HttpResponseStatus status = OK;
        private HttpHeaders.Builder headers;
        private HttpVersion version = HTTP_1_1;
        private boolean validate = true;
        private byte[] body;

        public Builder() {
            this.headers = new HttpHeaders.Builder();
            this.body = new byte[0];
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
        }

        public Builder(HttpResponse response, byte[] decoded) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = decoded;
        }

        /**
         * Sets the response status.
         *
         * @param status response status
         * @return {@code this}
         */
        public Builder status(HttpResponseStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the request body.
         * <p>
         * This method encodes a String content to a byte array using the specified
         * charset, and sets the Content-Length header accordingly.
         *
         * @param content request body
         * @param charset Charset for string encoding.
         * @return {@code this}
         */
        public Builder body(String content, Charset charset) {
            return body(content, charset, true);
        }

        /**
         * Sets the response body.
         * <p>
         * This method encodes the content to a byte array using the specified
         * charset, and sets the Content-Length header *if* the setContentLength
         * argument is true.
         *
         * @param content          response body
         * @param charset          Charset used for encoding response body.
         * @param setContentLength If true, Content-Length header is set, otherwise it is not set.
         * @return {@code this}
         */
        public Builder body(String content, Charset charset, boolean setContentLength) {
            requireNonNull(charset, "Charset is not provided.");
            String sanitised = content == null ? "" : content;
            return body(sanitised.getBytes(charset), setContentLength);
        }

        /**
         * Sets the response body.
         * <p>
         * This method encodes the content to a byte array provided, and
         * sets the Content-Length header *if* the setContentLength
         * argument is true.
         *
         * @param content          response body
         * @param setContentLength If true, Content-Length header is set, otherwise it is not set.
         * @return {@code this}
         */
        public Builder body(byte[] content, boolean setContentLength) {
            this.body = content == null ? new byte[0] : content.clone();

            if (setContentLength) {
                header(CONTENT_LENGTH, this.body.length);
            }

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
         * Sets the cookies on this response by removing existing "Set-Cookie" headers and adding new ones.
         *
         * @param cookies cookies
         * @return this builder
         */
        public Builder cookies(ResponseCookie... cookies) {
            return cookies(asList(cookies));
        }

        /**
         * Sets the cookies on this response by removing existing "Set-Cookie" headers and adding new ones.
         *
         * @param cookies cookies
         * @return this builder
         */
        public Builder cookies(Collection<ResponseCookie> cookies) {
            requireNonNull(cookies);
            headers.remove(SET_COOKIE);
            return addCookies(cookies);
        }

        /**
         * Adds cookies into this response by adding "Set-Cookie" headers.
         *
         * @param cookies cookies
         * @return this builder
         */
        public Builder addCookies(ResponseCookie... cookies) {
            return addCookies(asList(cookies));
        }

        /**
         * Adds cookies into this response by adding "Set-Cookie" headers.
         *
         * @param cookies cookies
         * @return this builder
         */
        public Builder addCookies(Collection<ResponseCookie> cookies) {
            requireNonNull(cookies);

            if (cookies.isEmpty()) {
                return this;
            }

            removeCookies(cookies.stream().map(ResponseCookie::name).collect(toList()));

            encode(cookies).forEach(cookie ->
                    addHeader(SET_COOKIE, cookie));
            return this;
        }

        /**
         * Removes all cookies matching one of the supplied names by removing their "Set-Cookie" headers.
         *
         * @param names cookie names
         * @return this builder
         */
        public Builder removeCookies(String... names) {
            return removeCookies(asList(names));
        }

        /**
         * Removes all cookies matching one of the supplied names by removing their "Set-Cookie" headers.
         *
         * @param names cookie names
         * @return this builder
         */
        public Builder removeCookies(Collection<String> names) {
            requireNonNull(names);

            if (names.isEmpty()) {
                return this;
            }

            return removeCookiesIf(toSet(names)::contains);
        }

        private Builder removeCookiesIf(Predicate<String> removeIfName) {
            Predicate<ResponseCookie> keepIf = cookie -> !removeIfName.test(cookie.name());

            List<ResponseCookie> newCookies = decode(headers.getAll(SET_COOKIE)).stream()
                    .filter(keepIf)
                    .collect(toList());

            return cookies(newCookies);
        }

        private static <T> Set<T> toSet(Collection<T> collection) {
            return collection instanceof Set ? (Set<T>) collection : ImmutableSet.copyOf(collection);
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
