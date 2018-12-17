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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.HttpVersion.httpVersion;
import static com.hotels.styx.api.ResponseCookie.decode;
import static com.hotels.styx.api.ResponseCookie.encode;
import static io.netty.buffer.ByteBufUtil.getBytes;
import static java.lang.Long.parseLong;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * An HTTP response object with a byte stream body.
 * <p>
 * An {@code LiveHttpResponse} is used in {@link HttpInterceptor} where each content
 * chunk must be processed as they arrive. It is also useful for dealing with
 * very large content sizes, and in situations where content size is not known
 * upfront.
 * <p>
 * An {@code LiveHttpResponse} object is immutable with respect to the response
 * attributes and HTTP headers. Once an instance is created, they cannot change.
 * <p>
 * An {@code LiveHttpResponse} body is a byte buffer stream that can be consumed
 * as sequence of asynchronous events. Once consumed, the stream is exhausted and
 * can not be reused. Conceptually each {@code LiveHttpResponse} object has an
 * associated producer object that publishes data to the stream. For example,
 * a Styx Server implements a content producer for {@link HttpInterceptor}
 * extensions. The producer receives data chunks from a network socket and
 * publishes them to an appropriate content stream.
 * <p>
 * HTTP responses are created via {@code Builder} object, which can be created
 * with static helper methods:
 *
 * <ul>
 * <li>{@code response()}</li>
 * <li>{@code response(HttpResponseStatus)}</li>
 * <li>{@code response(HttpResponseStatus, Eventual<ByteBuf>)}</li>
 * </ul>
 * <p>
 * A builder can also be created with one of the {@code Builder} constructors.
 *
 * A special method {@code newBuilder} creates a prepopulated {@code Builder}
 * from the current response object. It is useful for transforming a response
 * to another one my modifying one or more of its attributes.
 */
public class LiveHttpResponse implements LiveHttpMessage {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final ByteStream body;

    LiveHttpResponse(Builder builder) {
        this.version = builder.version;
        this.status = builder.status;
        this.headers = builder.headers.build();
        this.body = builder.body;
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

    /**
     * Creates an HTTP response builder with a given status and body.
     *
     * @param status response status
     * @param body   response body
     * @return a new builder
     */
    public static Builder response(HttpResponseStatus status, ByteStream body) {
        return new Builder(status).body(body);
    }

    /**
     * @return all HTTP headers as an {@link HttpHeaders} object
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * @return the response body as a byte stream
     */
    @Override
    public ByteStream body() {
        return body;
    }

    /**
     * @return a HTTP protocol version
     */
    @Override
    public HttpVersion version() {
        return version;
    }

    /**
     * Return a new {@link LiveHttpResponse.Builder} that will inherit properties from this response.
     * <p>
     * This allows a new response to be made that is identical to this one
     * except for the properties overridden by the builder methods.
     *
     * @return new builder based on this response
     */
    public Transformer newBuilder() {
        return new Transformer(this);
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
     * Aggregates content stream and converts this response to a {@link HttpResponse}.
     * <p>
     * Returns a {@link Eventual <HttpResponse>} that eventually produces a
     * {@link HttpResponse}. The resulting full response object has the same
     * response line, headers, and content as this response.
     * <p>
     * The content stream is aggregated asynchronously. The stream may be connected
     * to a network socket or some other content producer. Once aggregated, a
     * HttpResponse object is emitted on the returned {@link Eventual}.
     * <p>
     * A sole {@code maxContentBytes} argument is a backstop defence against excessively
     * long content streams. The {@code maxContentBytes} should be set to a sensible
     * value according to your application requirements and heap size. When the content
     * size stream exceeds the {@code maxContentBytes}, a @{link ContentOverflowException}
     * is emitted on the returned observable.
     *
     * @param maxContentBytes maximum expected content size
     * @return a {@link Eventual}
     */
    public Eventual<HttpResponse> aggregate(int maxContentBytes) {
        return Eventual.from(body.aggregate(maxContentBytes))
                .map(it -> new HttpResponse.Builder(this, decodeAndRelease(it))
                    .disableValidation()
                    .build()
                );
    }

    private static byte[] decodeAndRelease(Buffer aggregate) {
        try {
            return getBytes(aggregate.delegate());
        } finally {
            aggregate.delegate().release();
        }
    }


    /**
     * Decodes "Set-Cookie" header values and returns them as set of {@link ResponseCookie} objects.
     *
     * @return a set of {@link ResponseCookie} objects
     */
    public Set<ResponseCookie> cookies() {
        return decode(headers.getAll(SET_COOKIE));
    }

    /**
     * Decodes a specified Set-Cookie header and returns it as a {@link ResponseCookie} object.
     *
     * @param name cookie name
     * @return an optional {@link ResponseCookie} value if corresponding cookie name is present,
     * or {@link Optional#empty} if not.
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
        LiveHttpResponse other = (LiveHttpResponse) obj;
        return Objects.equal(this.version, other.version)
                && Objects.equal(this.status, other.status)
                && Objects.equal(this.headers, other.headers);
    }

    private interface BuilderTransformer {
        /**
         * Sets the response status.
         *
         * @param status response status
         * @return {@code this}
         */
        BuilderTransformer status(HttpResponseStatus status);


        /**
         * Sets the HTTP version.
         *
         * @param version HTTP version
         * @return {@code this}
         */
        BuilderTransformer version(HttpVersion version);

        /**
         * Disables client-side caching of this response.
         *
         * @return {@code this}
         */
        BuilderTransformer disableCaching();

        /**
         * Makes this response chunked.
         *
         * @return {@code this}
         */
        BuilderTransformer setChunked();

        /**
         * Sets the cookies on this response by removing existing "Set-Cookie" headers and adding new ones.
         *
         * @param cookies cookies
         * @return {@code this}
         */
        BuilderTransformer cookies(ResponseCookie... cookies);

        /**
         * Sets the cookies on this response by removing existing "Set-Cookie" headers and adding new ones.
         *
         * @param cookies cookies
         * @return {@code this}
         */
        BuilderTransformer cookies(Collection<ResponseCookie> cookies);

        /**
         * Adds cookies into this response by adding "Set-Cookie" headers.
         *
         * @param cookies cookies
         * @return {@code this}
         */
        BuilderTransformer addCookies(ResponseCookie... cookies);
        /**
         * Adds cookies into this response by adding "Set-Cookie" headers.
         *
         * @param cookies cookies
         * @return {@code this}
         */
        BuilderTransformer addCookies(Collection<ResponseCookie> cookies);

        /**
         * Removes all cookies matching one of the supplied names by removing their "Set-Cookie" headers.
         *
         * @param names cookie names
         * @return {@code this}
         */
        BuilderTransformer removeCookies(String... names);

        /**
         * Removes all cookies matching one of the supplied names by removing their "Set-Cookie" headers.
         *
         * @param names cookie names
         * @return {@code this}
         */
        BuilderTransformer removeCookies(Collection<String> names);

        /**
         * Sets the (only) value for the header with the specified name.
         * <p/>
         * All existing values for the same header will be removed.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return {@code this}
         */
        BuilderTransformer header(CharSequence name, Object value);

        /**
         * Adds a new header with the specified {@code name} and {@code value}.
         * <p/>
         * Will not replace any existing values for the header.
         *
         * @param name  The name of the header
         * @param value The value of the header
         * @return {@code this}
         */
        BuilderTransformer addHeader(CharSequence name, Object value);

        /**
         * Removes the header with the specified name.
         *
         * @param name The name of the header to remove
         * @return {@code this}
         */
        BuilderTransformer removeHeader(CharSequence name);

        /**
         * (UNSTABLE) Removes body stream from this request.
         * <p>
         * This method is unstable until Styx 1.0 API is frozen.
         * <p>
         * Inappropriate use can lead to stability issues. These issues
         * will be addressed before Styx 1.0 API is frozen. Therefore
         * it is best to avoid until then. Consult
         * https://github.com/HotelsDotCom/styx/issues/201 for more
         * details.
         *
         * @return {@code this}
         */
        // TODO: See https://github.com/HotelsDotCom/styx/issues/201
        BuilderTransformer removeBody();


        /**
         * Sets the headers.
         *
         * @param headers headers
         * @return {@code this}
         */
        BuilderTransformer headers(HttpHeaders headers);

        /**
         * Disables automatic validation of some HTTP headers.
         * <p>
         * Normally the {@link Builder} validates the message when {@code build}
         * method is invoked. Specifically that:
         *
         * <li>
         * <ul>There is maximum of only one {@code Content-Length} header</ul>
         * <ul>The {@code Content-Length} header is zero or positive integer</ul>
         * </li>
         *
         * @return {@code this}
         */
        BuilderTransformer disableValidation();

        /**
         * Builds a new full response based on the settings configured in this builder.
         * <p>
         * Validates and builds a {link LiveHttpResponse} object. Object validation can be
         * disabled with {@link this.disableValidation} method.
         * <p>
         * When validation is enabled (by default), ensures that:
         *
         * <li>
         * <ul>There is maximum of only one {@code Content-Length} header</ul>
         * <ul>The {@code Content-Length} header is zero or positive integer</ul>
         * </li>
         *
         * @return a new full response.
         * @throws IllegalArgumentException when validation fails
         */
        LiveHttpResponse build();

    }

    public static final class Transformer implements BuilderTransformer {
        private final Builder builder;

        public Transformer(LiveHttpResponse response) {
            this.builder = new Builder(response);
        }

        /**
         * Transforms request body.
         *
         * @param transformation a Function from ByteStream to ByteStream.
         * @return a HttpResponhse builder with a transformed message body.
         */
        public Transformer body(Function<ByteStream, ByteStream> transformation) {
            this.builder.body(requireNonNull(transformation.apply(this.builder.body)));
            return this;
        }

        @Override
        public Transformer status(HttpResponseStatus status) {
            builder.status(status);
            return this;
        }

        @Override
        public Transformer version(HttpVersion version) {
            builder.version(version);
            return this;
        }

        @Override
        public Transformer disableCaching() {
            builder.disableCaching();
            return this;
        }

        @Override
        public Transformer setChunked() {
            builder.setChunked();
            return this;
        }

        @Override
        public Transformer cookies(ResponseCookie... cookies) {
            builder.cookies(cookies);
            return this;
        }

        @Override
        public Transformer cookies(Collection<ResponseCookie> cookies) {
            builder.cookies(cookies);
            return this;
        }

        @Override
        public Transformer addCookies(ResponseCookie... cookies) {
            builder.addCookies(cookies);
            return this;
        }

        @Override
        public Transformer addCookies(Collection<ResponseCookie> cookies) {
            builder.addCookies(cookies);
            return this;
        }

        @Override
        public Transformer removeCookies(String... names) {
            builder.removeCookies(names);
            return this;
        }

        @Override
        public Transformer removeCookies(Collection<String> names) {
            builder.removeCookies(names);
            return this;
        }

        @Override
        public Transformer header(CharSequence name, Object value) {
            builder.header(name, value);
            return this;
        }

        @Override
        public Transformer addHeader(CharSequence name, Object value) {
            builder.addHeader(name, value);
            return this;
        }

        @Override
        public Transformer removeHeader(CharSequence name) {
            builder.removeHeader(name);
            return this;
        }

        @Override
        public Transformer removeBody() {
            builder.removeBody();
            return this;
        }

        @Override
        public Transformer headers(HttpHeaders headers) {
            builder.headers(headers);
            return this;
        }

        @Override
        public Transformer disableValidation() {
            builder.disableValidation();
            return this;
        }

        @Override
        public LiveHttpResponse build() {
            return this.builder.build();
        }
    }

    /**
     * An HTTP response builder.
     */
    public static final class Builder implements BuilderTransformer {
        private HttpResponseStatus status = OK;
        private HttpHeaders.Builder headers;
        private HttpVersion version = HTTP_1_1;
        private boolean validate = true;
        private ByteStream body;

        /**
         * Creates a new {@link Builder} object with default attributes.
         */
        public Builder() {
            this.headers = new HttpHeaders.Builder();
            this.body = new ByteStream(Flux.empty());
        }

        /**
         * Creates a new {@link Builder} object with specified response status.
         *
         * @param status a HTTP response status
         */
        public Builder(HttpResponseStatus status) {
            this();
            this.status = status;
        }

        /**
         * Creates a new {@link Builder} object from an existing {@link LiveHttpResponse} object.
         * Similar to {@link this.newBuilder} method.
         *
         * @param response a response object for which the builder is based on
         */
        public Builder(LiveHttpResponse response) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = response.body();
        }

        /**
         * Creates a new {@link Builder} object from a response code and a content stream.
         * <p>
         * Builder's response status line parameters and the HTTP headers are populated from
         * the given {@code response} object, but the content stream is set to {@code ByteStream}.
         *
         * @param response      a full response for which the builder is based on
         * @param byteStream a content byte stream
         */
        public Builder(HttpResponse response, ByteStream byteStream) {
            this.status = statusWithCode(response.status().code());
            this.version = httpVersion(response.version().toString());
            this.headers = response.headers().newBuilder();
            this.body = requireNonNull(byteStream);
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
         * Sets the response body.
         *
         * @param content response body
         * @return {@code this}
         */
        public Builder body(ByteStream content) {
            this.body = requireNonNull(content);
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
         * Removes message body content from this request.
         * <p>
         * Transforms the content {@link ByteStream} to an empty stream such
         * that all received content events are discarded.
         *
         * @return {@code this}
         */
        public Builder removeBody() {
            this.body = body.drop();
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
         * Disables automatic validation of some HTTP headers.
         * <p>
         * Normally the {@link Builder} validates the message when {@code build}
         * method is invoked. Specifically that:
         *
         * <li>
         * <ul>There is maximum of only one {@code Content-Length} header</ul>
         * <ul>The {@code Content-Length} header is zero or positive integer</ul>
         * </li>
         *
         * @return {@code this}
         */
        public Builder disableValidation() {
            this.validate = false;
            return this;
        }

        /**
         * Builds a new full response based on the settings configured in this builder.
         * <p>
         * Validates and builds a {link LiveHttpResponse} object. Object validation can be
         * disabled with {@link this.disableValidation} method.
         * <p>
         * When validation is enabled (by default), ensures that:
         *
         * <li>
         * <ul>There is maximum of only one {@code Content-Length} header</ul>
         * <ul>The {@code Content-Length} header is zero or positive integer</ul>
         * </li>
         *
         * @return a new full response.
         * @throws IllegalArgumentException when validation fails
         */
        public LiveHttpResponse build() {
            if (validate) {
                ensureContentLengthIsValid();
            }

            return new LiveHttpResponse(this);
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
