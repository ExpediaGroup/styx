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

import com.google.common.collect.ImmutableSet;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.COOKIE;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.HttpHeaderValues.KEEP_ALIVE;
import static com.hotels.styx.api.HttpMethod.DELETE;
import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.api.HttpMethod.HEAD;
import static com.hotels.styx.api.HttpMethod.METHODS;
import static com.hotels.styx.api.HttpMethod.PATCH;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.api.HttpMethod.PUT;
import static com.hotels.styx.api.HttpMethod.httpMethod;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static com.hotels.styx.api.HttpVersion.httpVersion;
import static com.hotels.styx.api.RequestCookie.decode;
import static com.hotels.styx.api.RequestCookie.encode;
import static io.netty.buffer.ByteBufUtil.getBytes;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.lang.Long.parseLong;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

/**
 * An HTTP request object with a byte stream body.
 * <p>
 * An {@code LiveHttpRequest} is used in {@link HttpInterceptor} where each content
 * chunk must be processed as they arrive. It is also useful for dealing with
 * very large content sizes, and in situations where content size is not known
 * upfront.
 * <p>
 * An {@code LiveHttpRequest} object is immutable with respect to the request line
 * attributes and HTTP headers. Once an instance is created, they cannot change.
 * <p>
 * An {@code LiveHttpRequest} body is a byte buffer stream that can be consumed
 * as sequence of asynchronous events. Once consumed, the stream is exhausted and
 * can not be reused. Conceptually each {@code LiveHttpRequest} object
 * has an associated producer object that publishes data to the stream.
 * For example, a Styx Server implements a content producer for {@link HttpInterceptor}
 * extensions. The producer receives data chunks from a network socket and publishes
 * them to an appropriate content stream.
 * <p>
 * HTTP requests are created via {@code Builder} object, which can be created
 * with static helper methods:
 *
 * <ul>
 * <li>{@code get}</li>
 * <li>{@code head}</li>
 * <li>{@code post}</li>
 * <li>{@code put}</li>
 * <li>{@code delete}</li>
 * <li>{@code patch}</li>
 * </ul>
 * <p>
 * A builder can also be created with one of the {@code Builder} constructors.
 * <p>
 * A special method {@code newBuilder} creates a prepopulated {@code Builder}
 * from the current request object. It is useful for transforming a request
 * to another one my modifying one or more of its attributes.
 */
public class LiveHttpRequest implements LiveHttpMessage {
    private final Object id;
    private final HttpVersion version;
    private final HttpMethod method;
    private final Url url;
    private final HttpHeaders headers;
    private final ByteStream body;

    LiveHttpRequest(Builder builder) {
        this.id = builder.id == null ? randomUUID() : builder.id;
        this.version = builder.version;
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers.build();
        this.body = requireNonNull(builder.body);
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
     * Creates a request with the POST method.
     *
     * @param uri  URI
     * @param body body
     * @return {@code this}
     */
    public static Builder post(String uri, ByteStream body) {
        return new Builder(POST, uri).body(body);
    }

    /**
     * Creates a request with the PUT method.
     *
     * @param uri  URI
     * @param body body
     * @return {@code this}
     */
    public static Builder put(String uri, ByteStream body) {
        return new Builder(PUT, uri).body(body);
    }

    /**
     * Creates a request with the PATCH method.
     *
     * @param uri  URI
     * @param body body
     * @return {@code this}
     */
    public static Builder patch(String uri, ByteStream body) {
        return new Builder(PATCH, uri).body(body);
    }

    /**
     * @return HTTP protocol version
     */
    @Override
    public HttpVersion version() {
        return this.version;
    }

    /**
     * @return all HTTP headers as an {@link HttpHeaders} instance
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * @param name header name
     * @return all values for a given HTTP header name or an empty list if the header is not present
     */
    @Override
    public List<String> headers(CharSequence name) {
        return headers.getAll(name);
    }

    /**
     * @return request body as a byte stream
     */
    @Override
    public ByteStream body() {
        return body;
    }

    /**
     * @return an unique request ID
     */
    public Object id() {
        return id;
    }

    /**
     * @return the HTTP method
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * @return the request URL
     */
    public Url url() {
        return url;
    }

    /**
     * @return the request URL path component
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
     * @return the names of all query parameters
     */
    public Iterable<String> queryParamNames() {
        return url.queryParamNames();
    }

    /**
     * Return a new {@link Builder} that will inherit properties from this request.
     * <p>
     * This allows a new request to be made that is identical to this one
     * except for the properties overridden by the builder methods.
     *
     * @return new builder based on this request
     */
    public Transformer newBuilder() {
        return new Transformer(this);
    }

    /**
     * Aggregates content stream and converts this request to a {@link HttpRequest}.
     * <p>
     * Returns a {@link Eventual} that eventually produces a
     * {@link HttpRequest}. The resulting full request object has the same
     * request line, headers, and content as this request.
     * <p>
     * The content stream is aggregated asynchronously. The stream may be connected
     * to a network socket or some other content producer. Once aggregated, a
     * HttpRequest object is emitted on the returned {@link Eventual}.
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
    public Eventual<HttpRequest> aggregate(int maxContentBytes) {
        return Eventual.from(
                body.aggregate(maxContentBytes)
                    .thenApply(it -> new HttpRequest.Builder(this, decodeAndRelease(it)).build())
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
     * Decodes the "Cookie" header in this request and returns the cookies.
     *
     * @return a set of cookies
     */
    public Set<RequestCookie> cookies() {
        return headers.get(COOKIE)
                .map(RequestCookie::decode)
                .orElseGet(Collections::emptySet);
    }

    /**
     * Decodes the "Cookie" header in this request and returns the specified cookie.
     *
     * @param name cookie name
     * @return an optional cookie
     */
    public Optional<RequestCookie> cookie(String name) {
        return cookies().stream()
                .filter(cookie -> cookie.name().equals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("version", version)
                .add("method", method)
                .add("uri", url)
                .add("headers", headers)
                .add("id", id)
                .toString();
    }

    private interface BuilderTransformer {
        BuilderTransformer uri(String uri);

        BuilderTransformer id(Object id);

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
         * Sets the headers.
         *
         * @param headers headers
         * @return {@code this}
         */
        BuilderTransformer headers(HttpHeaders headers);

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
         * Sets the request fully qualified url.
         *
         * @param url fully qualified url
         * @return {@code this}
         */
        BuilderTransformer url(Url url);

        /**
         * Sets the HTTP version.
         *
         * @param version HTTP version
         * @return {@code this}
         */
        BuilderTransformer version(HttpVersion version);

        /**
         * Enable validation of uri and some headers.
         *
         * @return {@code this}
         */
        BuilderTransformer disableValidation();

        /**
         * Enables Keep-Alive.
         *
         * @return {@code this}
         */
        BuilderTransformer enableKeepAlive();

        /**
         * Sets the cookies on this request by overwriting the value of the "Cookie" header.
         *
         * @param cookies cookies
         * @return this builder
         */
        BuilderTransformer cookies(RequestCookie... cookies);

        /**
         * Sets the cookies on this request by overwriting the value of the "Cookie" header.
         *
         * @param cookies cookies
         * @return this builder
         */
        BuilderTransformer cookies(Collection<RequestCookie> cookies);

        /**
         * Adds cookies into the "Cookie" header. If the name matches an already existing cookie, the value will be overwritten.
         * <p>
         * Note that this requires decoding the current header value before re-encoding, so it is most efficient to
         * add all new cookies in one call to the method rather than spreading them out.
         *
         * @param cookies new cookies
         * @return this builder
         */
        BuilderTransformer addCookies(RequestCookie... cookies);

        /**
         * Adds cookies into the "Cookie" header. If the name matches an already existing cookie, the value will be overwritten.
         * <p>
         * Note that this requires decoding the current header value before re-encoding, so it is most efficient to
         * add all new cookies in one call to the method rather than spreading them out.
         *
         * @param cookies new cookies
         * @return this builder
         */
        BuilderTransformer addCookies(Collection<RequestCookie> cookies);

        /**
         * Removes all cookies matching one of the supplied names by overwriting the value of the "Cookie" header.
         *
         * @param names cookie names
         * @return this builder
         */
        BuilderTransformer removeCookies(String... names);

        /**
         * Removes all cookies matching one of the supplied names by overwriting the value of the "Cookie" header.
         *
         * @param names cookie names
         * @return this builder
         */
        BuilderTransformer removeCookies(Collection<String> names);

        /**
         * Builds a new full request based on the settings configured in this builder.
         * If {@code validate} is set to true:
         * <ul>
         * <li>the host header will be set if absent</li>
         * <li>an exception will be thrown if the content length is not an integer, or more than one content length exists</li>
         * <li>an exception will be thrown if the request method is not a valid HTTP method</li>
         * </ul>
         *
         * @return a new full request
         */
        LiveHttpRequest build();
    }

    public static final class Transformer implements BuilderTransformer {
        private final Builder builder;

        public Transformer(LiveHttpRequest liveHttpRequest) {
            this.builder = new Builder(liveHttpRequest);
        }

        @Override
        public Transformer uri(String uri) {
            builder.uri(uri);
            return this;
        }

        /**
         * Transforms request body.
         *
         * @param transformation a Function from ByteStream to ByteStream.
         * @return a LiveHttpRequest builder with a transformed message body.
         */
        public Transformer body(Function<ByteStream, ByteStream> transformation) {
            builder.body(transformation.apply(builder.body));
            return this;
        }

        @Override
        public Transformer id(Object id) {
            builder.id(id);
            return this;
        }

        @Override
        public Transformer header(CharSequence name, Object value) {
            builder.header(name, value);
            return this;
        }

        @Override
        public Transformer headers(HttpHeaders headers) {
            builder.headers(headers);
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
        public Transformer url(Url url) {
            builder.url(url);
            return this;
        }

        @Override
        public Transformer version(HttpVersion version) {
            builder.version(version);
            return this;
        }

        @Override
        public Transformer disableValidation() {
            builder.disableValidation();
            return this;
        }

        @Override
        public Transformer enableKeepAlive() {
            builder.enableKeepAlive();
            return this;
        }

        @Override
        public Transformer cookies(RequestCookie... cookies) {
            builder.cookies(cookies);
            return this;
        }

        @Override
        public Transformer cookies(Collection<RequestCookie> cookies) {
            builder.cookies(cookies);
            return this;
        }

        @Override
        public Transformer addCookies(RequestCookie... cookies) {
            builder.addCookies(cookies);
            return this;
        }

        @Override
        public Transformer addCookies(Collection<RequestCookie> cookies) {
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
        public LiveHttpRequest build() {
            return builder.build();
        }
    }

    /**
     * An HTTP request builder.
     */
    public static final class Builder implements BuilderTransformer {
        private Object id;
        private HttpMethod method = HttpMethod.GET;
        private boolean validate = true;
        private Url url;
        private HttpHeaders.Builder headers;
        private HttpVersion version = HTTP_1_1;
        private ByteStream body;

        /**
         * Creates a new {@link Builder} object with default attributes.
         */
        public Builder() {
            this.url = Url.Builder.url("/").build();
            this.headers = new HttpHeaders.Builder();
            this.body = new ByteStream(Flux.empty());
        }

        /**
         * Creates a new {@link Builder} with specified HTTP method and URI.
         *
         * @param method a HTTP method
         * @param uri    a HTTP URI
         */
        public Builder(HttpMethod method, String uri) {
            this();
            this.method = requireNonNull(method);
            this.url = Url.Builder.url(uri).build();
        }

        /**
         * Creates a new {@link Builder} from an existing request with a new body content stream.
         *
         * @param request       a HTTP request object
         * @param contentStream a body content stream
         */
        public Builder(LiveHttpRequest request, ByteStream contentStream) {
            this.id = request.id();
            this.method = httpMethod(request.method().name());
            this.url = request.url();
            this.version = httpVersion(request.version().toString());
            this.headers = request.headers().newBuilder();
            this.body = body;
        }

        Builder(LiveHttpRequest request) {
            this.id = request.id();
            this.method = request.method();
            this.url = request.url();
            this.version = request.version();
            this.headers = request.headers().newBuilder();
            this.body = request.body();
        }

        Builder(HttpRequest request) {
            this.id = request.id();
            this.method = request.method();
            this.url = request.url();
            this.version = request.version();
            this.headers = request.headers().newBuilder();
            this.body = new ByteStream(Flux.just(new Buffer(copiedBuffer(request.body()))));
        }

        /**
         * Sets the request URI.
         *
         * @param uri URI
         * @return {@code this}
         */
        @Override
        public Builder uri(String uri) {
            return this.url(Url.Builder.url(uri).build());
        }

        /**
         * Sets the request body.
         *
         * @param content request body
         * @return {@code this}
         */
        public Builder body(ByteStream content) {
            this.body = content;
            return this;
        }

        /**
         * Sets the unique ID for this request.
         *
         * @param id request ID
         * @return {@code this}
         */
        @Override
        public Builder id(Object id) {
            this.id = id;
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
        @Override
        public Builder header(CharSequence name, Object value) {
            this.headers.set(name, value);
            return this;
        }

        /**
         * Sets the headers.
         *
         * @param headers headers
         * @return {@code this}
         */
        @Override
        public Builder headers(HttpHeaders headers) {
            this.headers = headers.newBuilder();
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
        @Override
        public Builder addHeader(CharSequence name, Object value) {
            this.headers.add(name, value);
            return this;
        }

        /**
         * Removes the header with the specified name.
         *
         * @param name The name of the header to remove
         * @return {@code this}
         */
        @Override
        public Builder removeHeader(CharSequence name) {
            headers.remove(name);
            return this;
        }

        /**
         * Sets the request fully qualified url.
         *
         * @param url fully qualified url
         * @return {@code this}
         */
        @Override
        public Builder url(Url url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the HTTP version.
         *
         * @param version HTTP version
         * @return {@code this}
         */
        @Override
        public Builder version(HttpVersion version) {
            this.version = requireNonNull(version);
            return this;
        }

        /**
         * Sets the HTTP method.
         *
         * @param method HTTP method
         * @return {@code this}
         */
        public Builder method(HttpMethod method) {
            this.method = requireNonNull(method);
            return this;
        }

        /**
         * Enable validation of uri and some headers.
         *
         * @return {@code this}
         */
        @Override
        public Builder disableValidation() {
            this.validate = false;
            return this;
        }

        /**
         * Enables Keep-Alive.
         *
         * @return {@code this}
         */
        @Override
        public Builder enableKeepAlive() {
            return header(CONNECTION, KEEP_ALIVE);
        }

        /**
         * Sets the cookies on this request by overwriting the value of the "Cookie" header.
         *
         * @param cookies cookies
         * @return this builder
         */
        @Override
        public Builder cookies(RequestCookie... cookies) {
            return cookies(asList(cookies));
        }

        /**
         * Sets the cookies on this request by overwriting the value of the "Cookie" header.
         *
         * @param cookies cookies
         * @return this builder
         */
        @Override
        public Builder cookies(Collection<RequestCookie> cookies) {
            requireNonNull(cookies);

            headers.remove(COOKIE);

            if (!cookies.isEmpty()) {
                header(COOKIE, encode(cookies));
            }
            return this;
        }

        /**
         * Adds cookies into the "Cookie" header. If the name matches an already existing cookie, the value will be overwritten.
         * <p>
         * Note that this requires decoding the current header value before re-encoding, so it is most efficient to
         * add all new cookies in one call to the method rather than spreading them out.
         *
         * @param cookies new cookies
         * @return this builder
         */
        @Override
        public Builder addCookies(RequestCookie... cookies) {
            return addCookies(asList(cookies));
        }

        /**
         * Adds cookies into the "Cookie" header. If the name matches an already existing cookie, the value will be overwritten.
         * <p>
         * Note that this requires decoding the current header value before re-encoding, so it is most efficient to
         * add all new cookies in one call to the method rather than spreading them out.
         *
         * @param cookies new cookies
         * @return this builder
         */
        @Override
        public Builder addCookies(Collection<RequestCookie> cookies) {
            requireNonNull(cookies);

            Set<RequestCookie> currentCookies = decode(headers.get(COOKIE));
            List<RequestCookie> newCookies = concat(cookies.stream(), currentCookies.stream()).collect(toList());

            return cookies(newCookies);
        }

        /**
         * Removes all cookies matching one of the supplied names by overwriting the value of the "Cookie" header.
         *
         * @param names cookie names
         * @return this builder
         */
        @Override
        public Builder removeCookies(String... names) {
            return removeCookies(asList(names));
        }

        /**
         * Removes all cookies matching one of the supplied names by overwriting the value of the "Cookie" header.
         *
         * @param names cookie names
         * @return this builder
         */
        @Override
        public Builder removeCookies(Collection<String> names) {
            requireNonNull(names);

            return removeCookiesIf(toSet(names)::contains);
        }

        private Builder removeCookiesIf(Predicate<String> removeIfName) {
            Predicate<RequestCookie> keepIf = cookie -> !removeIfName.test(cookie.name());

            List<RequestCookie> newCookies = decode(headers.get(COOKIE)).stream()
                    .filter(keepIf)
                    .collect(toList());

            return cookies(newCookies);
        }

        private static <T> Set<T> toSet(Collection<T> collection) {
            return collection instanceof Set ? (Set<T>) collection : ImmutableSet.copyOf(collection);
        }

        /**
         * Builds a new full request based on the settings configured in this builder.
         * If {@code validate} is set to true:
         * <ul>
         * <li>the host header will be set if absent</li>
         * <li>an exception will be thrown if the content length is not an integer, or more than one content length exists</li>
         * <li>an exception will be thrown if the request method is not a valid HTTP method</li>
         * </ul>
         *
         * @return a new full request
         */
        @Override
        public LiveHttpRequest build() {
            if (validate) {
                ensureContentLengthIsValid();
                requireNotDuplicatedHeader(COOKIE);
                ensureMethodIsValid();
                setHostHeader();
            }

            return new LiveHttpRequest(this);
        }

        private void setHostHeader() {
            url.authority()
                    .ifPresent(authority -> header(HOST, authority.hostAndPort()));
        }

        private void ensureMethodIsValid() {
            checkArgument(isMethodValid(), "Unrecognised HTTP method=%s", this.method);
        }

        private boolean isMethodValid() {
            return METHODS.contains(this.method);
        }

        private void ensureContentLengthIsValid() {
            requireNotDuplicatedHeader(CONTENT_LENGTH).ifPresent(contentLength ->
                    checkArgument(isNonNegativeInteger(contentLength), "Invalid Content-Length found. %s", contentLength)
            );
        }

        private Optional<String> requireNotDuplicatedHeader(CharSequence headerName) {
            List<String> headerValues = headers.build().getAll(headerName);

            checkArgument(headerValues.size() <= 1, "Duplicate %s found. %s", headerName, headerValues);

            return headerValues.isEmpty() ? Optional.empty() : Optional.of(headerValues.get(0));
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
