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
import reactor.core.publisher.Flux;

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
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.ResponseCookie.decode;
import static com.hotels.styx.api.ResponseCookie.encode;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.lang.Long.parseLong;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * An immutable HTTP response object including full body content.
 * <p>
 * A {@link HttpResponse} is useful for responses with a
 * finite body content, such as when a REST API object is returned as a
 * response to a GET request.
 * <p>
 * A HttpResponse is created via {@link HttpResponse.Builder}. A new builder
 * can be obtained by a call to following static methods:
 *
 * <ul>
 *     <li>{@code response()}</li>
 *     <li>{@code response(HttpResponseStatus)}</li>
 * </ul>
 *
 * A builder can also be created with one of the {@code Builder} constructors.
 *
 * HttpResponse is immutable. Once created it cannot be modified.
 * However a response can be transformed to another using the {@link this#newBuilder}
 * method. It creates a new {@link Builder} with all message properties and
 * body content cloned in.
 */
public class HttpResponse implements HttpMessage {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final byte[] body;

    private HttpResponse(Builder builder) {
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
     * @param charset Charset used to decode message body
     * @return message body as a String
     */
    @Override
    public String bodyAs(Charset charset) {
        // CHECKSTYLE:OFF
        return new String(body, charset);
        // CHECKSTYLE:ON
    }

    /**
     * @return a HTTP protocol version
     */
    @Override
    public HttpVersion version() {
        return version;
    }

    /**
     * Return a new {@link HttpResponse.Builder} that will inherit properties from this response.
     * <p>
     * This allows a new response to be made that is identical to this one
     * except for the properties overridden by the builder methods.
     *
     * @return new builder based on this response
     */
    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * @return an HTTP response status
     */
    public HttpResponseStatus status() {
        return status;
    }

    /**
     * @return true if this response is a redirect
     */
    public boolean isRedirect() {
        return status.code() >= 300 && status.code() < 400;
    }

    /**
     * Converts this response to a streaming form (LiveHttpResponse).
     * <p>
     * Converts this response to an LiveHttpResponse object which represents the HTTP response as a
     * stream of bytes.
     *
     * @return A streaming LiveHttpResponse object
     */
    public LiveHttpResponse stream() {
        if (this.body.length == 0) {
            return new LiveHttpResponse.Builder(this, new ByteStream(Flux.empty())).build();
        } else {
            return new LiveHttpResponse.Builder(this, new ByteStream(Flux.just(new Buffer(copiedBuffer(this.body))))).build();
        }
    }

    /**
     * Decodes the "Set-Cookie" headers in this response and returns the cookies.
     *
     * @return a set of cookies
     */
    public Set<ResponseCookie> cookies() {
        return decode(headers.getAll(SET_COOKIE));
    }

    /**
     * Decodes the "Set-Cookie" headers in this response and returns the specified cookie.
     *
     * @param name cookie name
     * @return an optional cookie
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
        HttpResponse other = (HttpResponse) obj;
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

        /**
         * Creates a new {@link Builder} object with default attributes.
         */
        public Builder() {
            this.headers = new HttpHeaders.Builder();
            this.body = new byte[0];
        }

        /**
         * Creates a new {@link Builder} object with specified response status.
         *
         * @param status an HTTP response status
         */
        public Builder(HttpResponseStatus status) {
            this();
            this.status = status;
        }

        /**
         * Creates a new {@link Builder} object from an existing {@link LiveHttpResponse} object.
         * Similar to {@link this.newBuilder} method.
         *
         * @param response a full HTTP response instance
         */
        public Builder(HttpResponse response) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = response.body();
        }

        /**
         * Creates a new {@link Builder} object from a response code and a content byte array.
         *
         * @param response a streaming HTTP response instance
         * @param body a HTTP message body
         */
        public Builder(LiveHttpResponse response, byte[] body) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = body;
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
         * @param charset charset for string encoding
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
         * @param charset          charset used for encoding response body
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
         * @return {@code this}
         */
        public Builder cookies(ResponseCookie... cookies) {
            return cookies(asList(cookies));
        }

        /**
         * Sets the cookies on this response by removing existing "Set-Cookie" headers and adding new ones.
         *
         * @param cookies cookies
         * @return {@code this}
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
         * @return {@code this}
         */
        public Builder addCookies(ResponseCookie... cookies) {
            return addCookies(asList(cookies));
        }

        /**
         * Adds cookies into this response by adding "Set-Cookie" headers.
         *
         * @param cookies cookies
         * @return {@code this}
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
         * @return {@code this}
         */
        public Builder removeCookies(String... names) {
            return removeCookies(asList(names));
        }

        /**
         * Removes all cookies matching one of the supplied names by removing their "Set-Cookie" headers.
         *
         * @param names cookie names
         * @return {@code this}
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
        public HttpResponse build() {
            if (validate) {
                ensureContentLengthIsValid();
            }

            return new HttpResponse(this);
        }

        Builder ensureContentLengthIsValid() {
            List<String> contentLengths = headers.build().getAll(CONTENT_LENGTH);

            checkArgument(contentLengths.size() <= 1, "Duplicate Content-Length found. %s", contentLengths);

            if (contentLengths.size() == 1) {
                checkArgument(isNonNegativeInteger(contentLengths.get(0)), "Invalid Content-Length found. %s", contentLengths.get(0));
            }
            return this;
        }

        private static boolean isNonNegativeInteger(String contentLength) {
            try {
                long value = parseLong(contentLength);
                return value >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
