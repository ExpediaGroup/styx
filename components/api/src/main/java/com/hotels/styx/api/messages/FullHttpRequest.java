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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.HttpMessageSupport;
import com.hotels.styx.api.Url;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderValues.KEEP_ALIVE;
import static com.hotels.styx.api.messages.HttpSupport.encodeBody;
import static com.hotels.styx.api.support.CookiesSupport.isCookieHeader;
import static io.netty.handler.codec.http.HttpMethod.CONNECT;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.PATCH;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpMethod.TRACE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.lang.Integer.parseInt;
import static java.net.InetSocketAddress.createUnresolved;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

/**
 * Request.
 *
 * @param <T> content type
 */
public class FullHttpRequest<T> implements FullHttpMessage<T> {
    private final Object id;
    private final InetSocketAddress clientAddress;
    private final HttpVersion version;
    private final HttpMethod method;
    private final Url url;
    private final HttpHeaders headers;
    private final boolean secure;
    private final T body;
    private final List<HttpCookie> cookies;

    FullHttpRequest(Builder<T> builder) {
        this.id = builder.id();
        this.clientAddress = builder.clientAddress();
        this.version = builder.version();
        this.method = builder.method();
        this.url = builder.url();
        this.secure = builder.secure();
        this.headers = builder.headers();
        this.body = builder.body();
        this.cookies = builder.cookies();
    }

    @Override
    public HttpVersion version() {
        return this.version;
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
    public List<String> headers(CharSequence name) {
        return headers.getAll(name);
    }

    @Override
    public T body() {
        return body;
    }

    /**
     * Gets the unique ID for this request.
     *
     * @return request ID
     */
    public Object id() {
        return id;
    }

    /**
     * Returns the {@link HttpMethod} of this.
     *
     * @return the HTTP method
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * Returns the requested URI (or alternatively, path).
     *
     * @return The URI being requested
     */
    public Url url() {
        return url;
    }

    /**
     * Returns the requested path.
     *
     * @return the path being requested
     */
    public String path() {
        return url.path();
    }

    /**
     * Returns {@code true} if and only if the connection can remain open and thus 'kept alive'.
     * This methods respects the value of the {@code "Connection"} header first and if this has no such header
     * then the keep-alive status is determined by the HTTP version, that is, HTTP/1.1 is keep-alive by default,
     * HTTP/1.0 is not keep-alive by default.
     *
     * @return true if the connection is keep-alive
     */
    public boolean keepAlive() {
        return HttpMessageSupport.keepAlive(headers, version);
    }

    /**
     * Checks if the request has been transferred over a secure connection. If the protocol is HTTPS and the
     * content is delivered over SSL then the request is considered to be secure.
     *
     * @return true if the request is transferred securely
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * Returns the remote client address that initiated the current request.
     *
     * @return the client address for this request
     */
    public InetSocketAddress clientAddress() {
        return this.clientAddress;
    }

    /**
     * Get a query parameter by name if present.
     *
     * @param name parameter name
     * @return query parameter if present
     */
    public Optional<String> queryParam(String name) {
        return url.queryParam(name);
    }

    /**
     * Gets query parameters by name.
     *
     * @param name parameter name
     * @return query parameters
     */
    public Iterable<String> queryParams(String name) {
        return url.queryParams(name);
    }

    /**
     * Get all query parameters.
     *
     * @return all query parameters
     */
    public Map<String, List<String>> queryParams() {
        return url.queryParams();
    }

    /**
     * Get the names of all query parameters.
     *
     * @return the names of all query parameters.
     */
    public Iterable<String> queryParamNames() {
        return url.queryParamNames();
    }

    /**
     * Return a new {@link Builder} that will inherit properties from this request.
     * This allows a new request to be made that will be identical to this one except for the properties
     * overridden by the builder methods.
     *
     * @return new builder based on this request
     */
    public Builder<T> newBuilder() {
        return new Builder<>(this);
    }

    /**
     * Encodes this request into a streaming form, using the provided encoder to transform the body from an arbitrary type
     * to a buffer of bytes.
     *
     * @param encoder an encoding function
     * @return an encoded (streaming) request
     */
    public com.hotels.styx.api.HttpRequest toStreamingHttpRequest(Function<T, ByteBuf> encoder) {
        return new com.hotels.styx.api.HttpRequest.Builder(this, encodeBody(this.body, encoder))
                .build();
    }

    /**
     * Encodes a request with a body of type String into a streaming form, using a UTF-8 encoding.
     *
     * @param request a request
     * @return an encoded (streaming) request
     */
    public static com.hotels.styx.api.HttpRequest toStreamingHttpRequest(FullHttpRequest<String> request) {
        return request.toStreamingHttpRequest(string -> Unpooled.copiedBuffer(string, UTF_8));
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("version", version)
                .add("method", method)
                .add("uri", url)
                .add("headers", headers)
                .add("cookies", cookies)
                .add("id", id)
                .add("clientAddress", clientAddress)
                .add("secure", secure)
                .toString();
    }

    /**
     * Builder.
     *
     * @param <T> body type
     */
    public static final class Builder<T> {
        private static final InetSocketAddress LOCAL_HOST = createUnresolved("127.0.0.1", 0);

        private Object id;
        private HttpMethod method = GET;
        private InetSocketAddress clientAddress = LOCAL_HOST;
        private boolean validate = true;
        private Url url;
        private boolean secure;
        private final HttpHeaders.Builder headers;
        private HttpVersion version = HTTP_1_1;
        private T body;
        private final List<HttpCookie> cookies;

        public Builder() {
            this.url = Url.Builder.url("/").build();
            this.headers = new HttpHeaders.Builder();
            this.body = null;
            this.cookies = new ArrayList<>();
        }

        public Builder(HttpMethod method, String uri) {
            this();
            this.method = requireNonNull(method);
            this.url = Url.Builder.url(uri).build();
            this.secure = url.isSecure();
        }

        public Builder(com.hotels.styx.api.HttpRequest request, T body) {
            this.id = request.id();
            this.method = request.method();
            this.clientAddress = request.clientAddress();
            this.url = request.url();
            this.secure = request.isSecure();
            this.version = request.version();
            this.headers = request.headers().newBuilder();
            this.body = body;
            this.cookies = new ArrayList<>(request.cookies());
        }

        Builder(FullHttpRequest<T> request) {
            this.id = request.id();
            this.method = request.method();
            this.clientAddress = request.clientAddress();
            this.url = request.url();
            this.secure = request.isSecure();
            this.version = request.version();
            this.headers = request.headers().newBuilder();
            this.body = request.body();
            this.cookies = new ArrayList<>(request.cookies());
        }

        private Builder(FullHttpRequest<?> request, boolean doNotCopyBody) {
            checkArgument(doNotCopyBody);
            this.id = request.id();
            this.method = request.method();
            this.clientAddress = request.clientAddress();
            this.url = request.url();
            this.secure = request.isSecure();
            this.version = request.version();
            this.headers = request.headers().newBuilder();
            this.body = null;
            this.cookies = new ArrayList<>(request.cookies());
        }

        /**
         * Creates a request with the GET method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> get(String uri) {
            return new Builder<>(GET, uri);
        }

        /**
         * Creates a request with the HEAD method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> head(String uri) {
            return new Builder<>(HEAD, uri);
        }

        /**
         * Creates a request with the POST method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> post(String uri) {
            return new Builder<>(POST, uri);
        }

        /**
         * Creates a request with the DELETE method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> delete(String uri) {
            return new Builder<>(DELETE, uri);
        }

        /**
         * Creates a request with the PUT method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> put(String uri) {
            return new Builder<>(PUT, uri);
        }

        /**
         * Creates a request with the PATCH method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static <T> Builder<T> patch(String uri) {
            return new Builder<>(PATCH, uri);
        }

        /**
         * Sets the request URI.
         *
         * @param uri URI
         * @return {@code this}
         */
        public Builder<T> uri(String uri) {
            return this.url(Url.Builder.url(uri).build());
        }

        public Builder<T> body(T content) {
            setContentLength(content);

            this.body = content;
            return this;
        }

        private void setContentLength(Object content) {
            header(CONTENT_LENGTH, HttpSupport.contentLength(content));
        }

        public Object id() {
            if (id == null) {
                id = randomUUID();
            }

            return id;
        }

        public Builder<T> header(CharSequence name, Object value) {
            checkNotCookie(name);
            this.headers.set(name, value);
            return this;
        }

        public Builder<T> id(Object id) {
            this.id = id;
            return this;
        }

        public Builder<T> addHeader(CharSequence name, Object value) {
            checkNotCookie(name);
            this.headers.add(name, value);
            return this;
        }

        public Builder<T> url(Url url) {
            this.url = url;
            this.secure = url.isSecure();
            return this;
        }

        public HttpMethod method() {
            return method;
        }

        public InetSocketAddress clientAddress() {
            return clientAddress;
        }

        public boolean validate() {
            return validate;
        }

        public Url url() {
            return url;
        }

        public boolean secure() {
            return secure;
        }

        public HttpHeaders headers() {
            return headers.build();
        }

        public HttpVersion version() {
            return version;
        }

        public T body() {
            return body;
        }

        public Builder<T> version(HttpVersion version) {
            this.version = requireNonNull(version);
            return this;
        }

        public Builder<T> method(HttpMethod method) {
            this.method = requireNonNull(method);
            return this;
        }

        public List<HttpCookie> cookies() {
            return ImmutableList.copyOf(cookies);
        }

        /**
         * Adds a response cookie (adds a new Set-Cookie header).
         *
         * @param cookie cookie to add
         * @return {@code this}
         */
        public Builder<T> addCookie(HttpCookie cookie) {
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
        public Builder<T> addCookie(String name, String value) {
            return addCookie(HttpCookie.cookie(name, value));
        }

        /**
         * Removes a cookie if present (removes its Set-Cookie header).
         *
         * @param name name of the cookie
         * @return {@code this}
         */
        public Builder<T> removeCookie(String name) {
            cookies.stream()
                    .filter(cookie -> cookie.name().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(cookie -> cookies.remove(cookie));

            return this;
        }

        public Builder<T> clientAddress(InetSocketAddress clientAddress) {
            this.clientAddress = requireNonNull(clientAddress);
            return this;
        }

        /**
         * Removes the header with the specified name.
         *
         * @param name The name of the header to remove
         * @return {@code this}
         */
        public Builder<T> removeHeader(CharSequence name) {
            headers.remove(name);
            return this;
        }

        public Builder<T> secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Enable validation of uri and some headers.
         *
         * @return {@code this}
         */
        public Builder<T> disableValidation() {
            this.validate = false;
            return this;
        }


        public Builder<T> enableKeepAlive() {
            return header(CONNECTION, KEEP_ALIVE);
        }

        public FullHttpRequest<T> build() {
            if (validate) {
                ensureContentLengthIsValid();
                ensureMethodIsValid();
            }

            setHostHeader();

            return new FullHttpRequest<T>(this);
        }

        private void setHostHeader() {
            url.authority()
                    .ifPresent(authority -> header(HOST, authority.hostAndPort()));
        }

        private void ensureMethodIsValid() {
            checkArgument(isMethodValid(), "Unrecognised HTTP method=%s", this.method);
        }

        private static void checkNotCookie(CharSequence name) {
            checkArgument(!isCookieHeader(name.toString()), "Cookies must be set with addCookie method");
        }

        private boolean isMethodValid() {
            return this.method == CONNECT
                    || this.method == DELETE
                    || this.method == GET
                    || this.method == HEAD
                    || this.method == OPTIONS
                    || this.method == PATCH
                    || this.method == POST
                    || this.method == PUT
                    || this.method == TRACE;
        }

        private void ensureContentLengthIsValid() {
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
    }
}
