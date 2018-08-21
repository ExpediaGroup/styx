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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.ReferenceCountUtil;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.FlowControlDisableOperator.disableFlowControl;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.ResponseCookie.decode;
import static com.hotels.styx.api.ResponseCookie.encode;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.HttpVersion.httpVersion;
import static io.netty.buffer.ByteBufUtil.getBytes;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.util.ReferenceCountUtil.release;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * HTTP response with a fully aggregated/decoded body.
 */
public class HttpResponse implements StreamingHttpMessage {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final StyxObservable<ByteBuf> body;

    HttpResponse(Builder builder) {
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
    public static Builder response(HttpResponseStatus status, StyxObservable<ByteBuf> body) {
        return new Builder(status).body(body);
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
    public StyxObservable<ByteBuf> body() {
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
        return status.code() >= 300 && status.code() < 400;
    }

    public StyxObservable<FullHttpResponse> toFullResponse(int maxContentBytes) {
        CompositeByteBuf byteBufs = compositeBuffer();

        Observable<FullHttpResponse> delegate = ((StyxCoreObservable<ByteBuf>) body)
                .delegate()
                .lift(disableFlowControl())
                .doOnError(e -> byteBufs.release())
                .collect(() -> byteBufs, (composite, part) -> {
                    long newSize = composite.readableBytes() + part.readableBytes();

                    if (newSize > maxContentBytes) {
                        release(composite);
                        release(part);

                        throw new ContentOverflowException(format("Maximum content size exceeded. Maximum size allowed is %d bytes.", maxContentBytes));
                    }
                    composite.addComponent(part);
                    composite.writerIndex(composite.writerIndex() + part.readableBytes());
                })
                .map(HttpResponse::decodeAndRelease)
                .map(decoded -> new FullHttpResponse.Builder(this, decoded)
                        .disableValidation()
                        .build());

        return new StyxCoreObservable<>(delegate);
    }

    private static byte[] decodeAndRelease(CompositeByteBuf aggregate) {
        try {
            return getBytes(aggregate);
        } finally {
            aggregate.release();
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
        private StyxObservable<ByteBuf> body;

        public Builder() {
            this.headers = new HttpHeaders.Builder();
            this.body = new StyxCoreObservable<>(Observable.empty());
        }

        public Builder(HttpResponseStatus status) {
            this();
            this.status = status;
        }

        public Builder(HttpResponse response) {
            this.status = response.status();
            this.version = response.version();
            this.headers = response.headers().newBuilder();
            this.body = response.body();
        }

        public Builder(FullHttpResponse response, StyxObservable<ByteBuf> decoded) {
            this.status = statusWithCode(response.status().code());
            this.version = httpVersion(response.version().toString());
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
         * Sets the response body.
         *
         * @param content response body
         * @return {@code this}
         */
        public Builder body(StyxObservable<ByteBuf> content) {
            this.body = content;
            return this;
        }

        /**
         * Sets the message body by encoding a {@link StyxObservable} of {@link String}s into bytes.
         *
         * @param contentObservable message body content.
         * @param charset           character set
         * @return {@code this}
         */
        public Builder body(StyxObservable<String> contentObservable, Charset charset) {
            return body(contentObservable.map(content -> copiedBuffer(content, charset)));
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
        public <T> Builder removeCookies(Collection<String> names) {
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
         * Removes body of the request.
         *
         * @return {@code this}
         */
        // TODO: See https://github.com/HotelsDotCom/styx/issues/201
        public Builder removeBody() {
            Observable<ByteBuf> delegate = ((StyxCoreObservable<ByteBuf>) body)
                    .delegate()
                    .doOnNext(ReferenceCountUtil::release)
                    .ignoreElements();

            return body(new StyxCoreObservable<>(delegate));
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
