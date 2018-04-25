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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.messages.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.CONNECT;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.PATCH;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpMethod.TRACE;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.UUID.randomUUID;

/**
 * Represents an HTTP request.
 * You can build an instance using {@link HttpRequest.Builder}.
 */
public final class HttpRequest implements HttpMessage {
    private final Object id;
    private final InetSocketAddress clientAddress;
    private final HttpVersion version;
    private final HttpMethod method;
    private final Url url;
    private final HttpHeaders headers;
    private final HttpMessageBody body;
    private final boolean secure;

    private final ImmutableList<HttpCookie> cookies;

    // Lazily created
    private volatile HttpPostRequestDecoder postRequestDecoder;

    private HttpRequest(Builder builder) {
        this.id = builder.id != null ? builder.id : randomUUID();
        this.clientAddress = builder.clientAddress;
        this.version = builder.version();
        this.method = builder.method;
        this.url = builder.url;
        this.secure = builder.secure;
        this.headers = builder.headers().build();
        this.body = builder.body();
        this.cookies = ImmutableList.copyOf(builder.cookies);
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
     * Returns the protocol version of this {@link HttpRequest}.
     *
     * @return the protocol version
     */
    @Override
    public HttpVersion version() {
        return this.version;
    }

    /**
     * Returns the {@link HttpMethod} of this {@link HttpRequest}.
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
     * Returns the value of the header with the specified {@code name}.
     * If there is more than one header value for the specified header name, the first value is returned.
     *
     * @return the value of the header with the specified {@code name} if present
     */
    public Optional<String> header(CharSequence name) {
        return headers.get(name);
    }

    /**
     * Return all headers in this request.
     *
     * @return all headers
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Returns the values of the headers with the specified {@code name}.
     *
     * @param name the name of the headers
     * @return A {@link List} of header values which will be empty if no values
     * are found
     */
    public ImmutableList<String> headers(CharSequence name) {
        return headers.getAll(name);
    }

    /**
     * Return the HTTP body of this request.
     *
     * @return HTTP body
     */
    public HttpMessageBody body() {
        return body;
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
     * Aggregates and converts this streaming request FullHttpRequest.
     * <p>
     * Aggregates up to maxContentLength bytes of HTTP request content stream. Once content is
     * aggregated, this streaming HttpRequest instance is converted to a FullHttpRequest object
     * with the aggregated content set as a message body.
     * <p>
     * This method aggregates the content stream asynchronously. Once the FullHttpRequest is
     * available, it will be emitted as an StyxObservable event. If the number of content bytes
     * exceeds maxContentLength an exception is emitted as StyxObservable error event.
     * <p>
     * Performance considerations: An instantiation of FullHttpRequest takes a copy of the aggregated
     * HTTP message content.
     * <p>
     * @param maxContentLength Maximum content bytes accepted from the HTTP content stream.
     * @return An {StyxObservable} that emits the FullHttpRequest once it is available.
     */
    public <T> StyxObservable<FullHttpRequest> toFullRequest(int maxContentLength) {
        return new StyxCoreObservable<>(body.aggregate(maxContentLength)
                .map(decoded -> new FullHttpRequest.Builder(this, decoded.copy().array()))
                .map(FullHttpRequest.Builder::build));
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
     * Returns an {@link Optional} containing the {@link HttpCookie} with the specified {@code name}
     * if such a cookie exists.
     *
     * @param name the name of the cookie
     * @return returns an optional cookie object from the header
     */
    public Optional<HttpCookie> cookie(String name) {
        return cookies().stream()
                .filter(cookie -> name.equalsIgnoreCase(cookie.name()))
                .findFirst();
    }

    /**
     * Return all cookies that were sent in the header.
     * If any cookies exists within the HTTP header they are returned
     * as {@code HttpCookie} objects. Otherwise it will return
     * empty list.
     *
     * @return all cookie objects from the HTTP header
     */
    public ImmutableList<HttpCookie> cookies() {
        return cookies;
    }

    /**
     * Return a new {@link HttpRequest.Builder} that will inherit properties from this request.
     * This allows a new request to be made that will be identical to this one except for the properties
     * overridden by the builder methods.
     *
     * @return new builder based on this request
     */
    public Builder newBuilder() {
        return new Builder(this);
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
                .toString();
    }


    /**
     * Return the {@code 'Content-Length'} header value.
     *
     * @return the content-length if present
     */
    public Optional<Integer> contentLength() {
        return header(CONTENT_LENGTH).map(Integer::valueOf);
    }

    /**
     * Return {@code true} if the response is chunked.
     *
     * @return {@code true} if the response is chunked
     */

    public boolean chunked() {
        return HttpMessageSupport.chunked(headers);
    }

    /**
     * A builder for {@link HttpRequest}.
     */
    public static final class Builder extends HttpMessageBuilder<Builder, HttpRequest> {
        private static final InetSocketAddress LOCAL_HOST = createUnresolved("127.0.0.1", 0);
        private Object id;
        private HttpMethod method;
        private InetSocketAddress clientAddress = LOCAL_HOST;
        private boolean validate = true;
        private Url url;
        private boolean secure;
        private List<HttpCookie> cookies = new ArrayList<>();

        private Builder(HttpRequest request) {
            this.id = request.id;
            this.secure = request.secure;
            this.url = request.url;
            this.method = request.method;
            this.clientAddress = request.clientAddress;
            this.cookies = new ArrayList<>(request.cookies);
            body(request.body);
            headers(request.headers.newBuilder());
            version(request.version);
        }

        public Builder(com.hotels.styx.api.messages.FullHttpRequest request) {
            this.id = request.id();
            this.secure = request.isSecure();
            this.url = request.url();
            this.method = HttpMethod.valueOf(request.method().name());
            this.cookies = new ArrayList<>(request.cookies());
            headers(request.headers().newBuilder());
            version(HttpVersion.valueOf(request.version().toString()));
        }

        /**
         * Creates a builder with an HTTP method.
         *
         * @param method HTTP method
         */
        public Builder(HttpMethod method) {
            this.method = method;
            headers(new HttpHeaders.Builder());
        }

        /**
         * Creates a builder with an HTTP method and URI.
         *
         * @param method HTTP method
         * @param uri    URI
         */
        public Builder(HttpMethod method, String uri) {
            this.method = method;
            this.url = Url.Builder.url(uri).build();
            this.secure = url.isSecure();
            headers(new HttpHeaders.Builder());
        }

        /**
         * Creates a request with the GET method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder get(String uri) {
            return new Builder(GET, uri);
        }

        /**
         * Creates a request with the HEAD method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder head(String uri) {
            return new Builder(HEAD, uri);
        }

        /**
         * Creates a request with the POST method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder post(String uri) {
            return new Builder(POST, uri);
        }

        /**
         * Creates a request with the DELETE method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder delete(String uri) {
            return new Builder(DELETE, uri);
        }

        /**
         * Creates a request with the PUT method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder put(String uri) {
            return new Builder(PUT, uri);
        }

        /**
         * Creates a request with the PATCH method.
         *
         * @param uri URI
         * @return {@code this}
         */
        public static Builder patch(String uri) {
            return new Builder(PATCH, uri);
        }

        /**
         * Sets the unique ID for this request.
         *
         * @param id request ID
         * @return {@code this}
         */
        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the HTTP method.
         *
         * @param method HTTP method
         * @return {@code this}
         */
        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        /**
         * Sets whether the request is be secure.
         *
         * @param secure true if secure
         * @return {@code this}
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Sets the request URI.
         *
         * @param uri URI
         * @return {@code this}
         */
        public Builder uri(String uri) {
            return this.url(Url.Builder.url(uri).build());
        }

        /**
         * Sets the request fully qualified url.
         *
         * @param url fully qualified url
         * @return {@code this}
         */
        public Builder url(Url url) {
            this.url = checkNotNull(url);
            this.secure = url.isSecure();
            return this;
        }

        /**
         * Adds a new cookie to the Cookie header. Creates the Cookie header if absent.
         *
         * @param cookie the cookie to add
         * @return {@code this}
         */

        public Builder addCookie(HttpCookie cookie) {
            cookies.add(checkNotNull(cookie));
            return this;
        }

        /**
         * Adds a new cookie to the Cookie header with the specified {@code name} and {@code value}. Creates the Cookie header if absent.
         *
         * @param name  The name of the cookie
         * @param value The value of the cookie
         * @return {@code this}
         */
        public Builder addCookie(String name, String value) {
            return addCookie(HttpCookie.cookie(name, value));
        }

        /**
         * Removes a cookie from the Cookie header if present.
         *
         * @param name cookie name
         * @return {@code this}
         */
        public Builder removeCookie(String name) {
            cookies.stream()
                    .filter(cookie -> cookie.name().equals(name))
                    .findFirst()
                    .ifPresent(cookies::remove);

            return this;
        }

        /**
         * Sets the client IP address.
         *
         * @param address IP address
         * @return {@code this}
         */
        public Builder clientAddress(InetSocketAddress address) {
            this.clientAddress = address;
            return this;
        }

        /**
         * Enables Keep-Alive.
         *
         * @return {@code this}
         */
        public Builder enableKeepAlive() {
            this.headers().add(CONNECTION, KEEP_ALIVE);
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
         * Builds a request.
         *
         * @return a request
         */
        public HttpRequest build() {
            if (validate) {
                ensureContentLengthIsValid();
                ensureMethodIsValid();
            }

            setHostHeader();
            return new HttpRequest(this);
        }

        private void setHostHeader() {
            url.authority()
                    .ifPresent(authority -> header(HOST, authority.hostAndPort()));
        }

        private void ensureMethodIsValid() {
            checkArgument(isMethodValid(), "Unrecognised HTTP method=%s", this.method);
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
    }
}

