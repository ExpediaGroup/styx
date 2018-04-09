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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.HttpMessageBody.NO_BODY;
import static com.hotels.styx.api.support.CookiesSupport.isCookieHeader;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.lang.Integer.parseInt;

/**
 * Base class for HttpRequest.Builder and HttpResponse.Builder.
 *
 * @param <SELF> this must be the same type as the subclass defining it
 * @param <MSG>  the type of message this builder can build
 */
abstract class HttpMessageBuilder<SELF extends HttpMessageBuilder<SELF, MSG>, MSG extends HttpMessage> {
    protected HttpMessageBody body = NO_BODY;
    private HttpHeaders.Builder headers;
    private HttpVersion version = HTTP_1_1;

    HttpMessageBody body() {
        return body;
    }

    HttpHeaders.Builder headers() {
        return headers;
    }

    void headers(HttpHeaders.Builder builder) {
        this.headers = builder;
    }

    HttpVersion version() {
        return version;
    }

    /**
     * Sets the HTTP version.
     *
     * @param version HTTP version
     * @return {@code this}
     */
    public SELF version(HttpVersion version) {
        this.version = version;
        return (SELF) this;
    }

    /**
     * Sets the message body.
     *
     * @param body message body
     * @return {@code this}
     */
    public SELF body(HttpMessageBody body) {
        this.body = body;
        return (SELF) this;
    }

    /**
     * Sets the message body.
     *
     * @param content message body content
     * @return {@code this}
     */
    public SELF body(Observable<ByteBuf> content) {
        return body(new HttpMessageBody(content));
    }

    /**
     * Sets the message body. As the content length is known, this header will also be set.
     *
     * @param content message body content.
     * @return {@code this}
     */
    public SELF body(ByteBuf content) {
        header(CONTENT_LENGTH, content.readableBytes());
        return body(Observable.just(content));
    }

    /**
     * Sets the message body. As the content length is known, this header will also be set.
     *
     * @param content message body content.
     * @return {@code this}
     */
    public SELF body(String content) {
        return content == null ? body(Unpooled.EMPTY_BUFFER) : body(content.getBytes());
    }

    /**
     * Sets the message body. As the content length is known, this header will also be set.
     *
     * @param content message body content.
     * @return {@code this}
     */
    public SELF body(byte[] content) {
        return body(Unpooled.copiedBuffer(content).retain());
    }

    /**
     * Sets the message body. As the content length is known, this header will also be set.
     *
     * @param content message body content.
     * @return {@code this}
     */
    public SELF body(ByteBuffer content) {
        return body(Unpooled.copiedBuffer(content));
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
    public SELF header(CharSequence name, String value) {
        checkNotCookie(name);
        headers.set(name, checkNotNull(value));
        return (SELF) this;
    }

    private static void checkNotCookie(CharSequence name) {
        checkArgument(!isCookieHeader(name.toString()), "Cookies must be set with addCookie method");
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
    public SELF header(CharSequence name, Object value) {
        checkNotCookie(name);
        headers.set(name, checkNotNull(value));
        return (SELF) this;
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
    public SELF header(CharSequence name, Instant value) {
        checkNotCookie(name);
        headers.set(name, checkNotNull(value));
        return (SELF) this;
    }

    /**
     * Sets the (only) value for the header with the specified name.
     * <p/>
     * All existing values for the same header will be removed.
     *
     * @param name   The name of the header
     * @param values The value of the header
     * @return {@code this}
     */
    public SELF header(CharSequence name, Iterable<?> values) {
        checkNotCookie(name);
        headers.set(name, values);
        return (SELF) this;
    }

    public SELF headers(HttpHeader... headers) {
        for (HttpHeader header : headers) {
            checkNotCookie(header.name());
            this.headers.set(header.name(), header.value());
        }
        return (SELF) this;
    }

    /**
     * Removes all headers on this builder and adds {@code headers}.
     *
     * @param headers headers
     * @return {@code this}
     */
    public SELF headers(HttpHeaders headers) {
        this.headers = headers.newBuilder();
        return (SELF) this;
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
    public SELF addHeader(CharSequence name, String value) {
        checkNotCookie(name);
        headers.add(name, value);
        return (SELF) this;
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
    public SELF addHeader(CharSequence name, Object value) {
        checkNotCookie(name);
        headers.add(name, value);
        return (SELF) this;
    }

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     * <p/>
     * Will not replace any existing values for the header.
     *
     * @param name   The name of the header
     * @param values The value of the header
     * @return {@code this}
     */
    public SELF addHeader(CharSequence name, Iterable<?> values) {
        checkNotCookie(name);
        headers.add(name, values);
        return (SELF) this;
    }

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     * @return {@code this}
     */
    public SELF removeHeader(CharSequence name) {
        headers.remove(name);
        return (SELF) this;
    }

    /**
     * Set the response to be chunked.
     *
     * @return {@code this}
     */
    public SELF chunked() {
        headers().add(TRANSFER_ENCODING, CHUNKED);
        headers().remove(CONTENT_LENGTH);
        return (SELF) this;
    }

    void ensureContentLengthIsValid() {
        List<String> contentLengths = headers().getAll(CONTENT_LENGTH);

        checkArgument(contentLengths.size() <= 1, "Duplicate Content-Length found. %s", contentLengths);

        if (contentLengths.size() == 1) {
            checkArgument(isInteger(contentLengths.get(0)), "Invalid Content-Length found. %s", contentLengths.get(0));
        }
    }

    private static boolean isInteger(String contentLength) {
        try {
            parseInt(contentLength);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Build a message (of the type that this builder supports).
     *
     * @return a message
     */
    public abstract MSG build();
}
