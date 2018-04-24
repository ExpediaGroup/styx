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
import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.v2.StyxObservable;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.netty.handler.codec.http.HttpResponseStatus.MULTIPLE_CHOICES;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
import static io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;

/**
 * Represents an HTTP response.
 * <p/>
 * You can build a {@link HttpResponse} using a {@link HttpResponse.Builder}.
 */
public final class HttpResponse implements HttpMessage {
    private static final Set<HttpResponseStatus> REDIRECT_STATUS = newHashSet(
            FOUND,
            SEE_OTHER,
            TEMPORARY_REDIRECT,
            MULTIPLE_CHOICES,
            MOVED_PERMANENTLY,
            TEMPORARY_REDIRECT);

    private final HttpRequest request;
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final HttpMessageBody body;

    private final ImmutableList<HttpCookie> cookies;

    private HttpResponse(Builder builder) {
        this.request = builder.request;
        this.version = builder.version();
        this.status = builder.status;
        this.headers = builder.headers().build();
        this.cookies = ImmutableList.copyOf(builder.cookies);
        this.body = builder.body();
    }

    /**
     * Returns the request that this response is responding to.
     *
     * @return the request
     */
    public HttpRequest request() {
        return request;
    }

    /**
     * Returns the protocol version of this {@link HttpResponse}.
     *
     * @return the protocol version
     */
    @Override
    public HttpVersion version() {
        return version;
    }

    /**
     * Returns the HTTP response status.
     *
     * @return HTTP response status
     */
    public HttpResponseStatus status() {
        return status;
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
     * Return all the headers in this response.
     *
     * @return all headers
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Return all cookies in this response.
     *
     * @return all cookies.
     */
    public ImmutableList<HttpCookie> cookies() {
        return cookies;
    }

    /**
     * Return the single cookie with the specified {@code name}.
     *
     * @param name cookie name
     * @return the cookie if present
     */
    public Optional<HttpCookie> cookie(String name) {
        return cookies().stream()
                .filter(cookie -> name.equalsIgnoreCase(cookie.name()))
                .findFirst();
    }

    /**
     * Return the body of the response.
     *
     * @return the body of the response
     */
    public HttpMessageBody body() {
        return body;
    }

    /**
     * Return a new {@link HttpResponse.Builder} that will inherit properties from this response.
     * This allows a new response to be made that will be identical to this one except for the properties
     * overridden by the builder methods.
     *
     * @return new builder based on this response
     */
    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * Returns {@code true} if this HttpResponse redirects to another resource.
     *
     * @return true if a redirect
     */
    public boolean isRedirect() {
        return REDIRECT_STATUS.contains(status);
    }

    /**
     * Returns the value of the {@code 'Content-Type'} header.
     *
     * @return content-type if present
     */
    public Optional<String> contentType() {
        return header(CONTENT_TYPE);
    }

    /**
     * Returns the value of the {@code 'Content-Length'} header.
     *
     * @return content-length if present
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
     * Aggregates and converts this streaming request FullHttpResponse.
     * <p>
     * Aggregates up to maxContentLength bytes of HTTP response content stream. Once content is
     * aggregated, this streaming HttpResponse instance is converted to a FullHttpResponse object
     * with the aggregated content set as a message body.
     * <p>
     * This method aggregates the content stream asynchronously. Once the FullHttpResponse is
     * available, it will be emitted as an Observable onNext event. If the number of content bytes
     * exceeds maxContentLength an exception is emitted as Observable onError event.
     * <p>
     * Performance considerations: An instantiation of FullHttpResponse takes a copy of the aggregated
     * HTTP message content.
     *
     * @param maxContentLength Maximum content bytes accepted from the HTTP content stream.
     * @return An {Observable} that emits the FullHttpResponse once it is available.
     */
    public StyxObservable<FullHttpResponse> toFullResponse(int maxContentLength) {
        // TODO: Mikko: Styx 2.0 API:
        // Fix this:
        return StyxObservable.from(body.aggregate(maxContentLength)
                .map(decoded -> new FullHttpResponse.Builder(this, decoded.copy().array()))
                .map(FullHttpResponse.Builder::build));
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("request", request)
                .add("version", version)
                .add("status", status)
                .add("headers", headers)
                .add("cookies", cookies)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(request, version, status, headers, cookies);
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
        return Objects.equal(this.request, other.request)
                && Objects.equal(this.version, other.version)
                && Objects.equal(this.status, other.status)
                && Objects.equal(this.headers, other.headers)
                && Objects.equal(this.cookies, other.cookies);
    }

    /**
     * A builder for {@link HttpResponse}.
     */
    public static class Builder extends HttpMessageBuilder<Builder, HttpResponse> {
        private HttpRequest request;
        private HttpResponseStatus status = OK;
        private boolean validate = true;
        private List<HttpCookie> cookies = new ArrayList<>();

        /**
         * Creates a builder with a status code.
         *
         * @param status status code
         */
        public Builder(HttpResponseStatus status) {
            this.status = status;
            headers(new HttpHeaders.Builder());
        }

        private Builder(HttpResponse response) {
            this.request = response.request;
            this.status = response.status;
            headers(response.headers.newBuilder());
            this.cookies = new ArrayList<>(response.cookies);
            version(response.version);
            body(response.body);
        }

        public Builder(FullHttpResponse response, Observable<ByteBuf> body) {
            this.status = HttpResponseStatus.valueOf(response.status().code());
            headers(response.headers().newBuilder());
            this.cookies = new ArrayList<>(response.cookies());
            version(HttpVersion.valueOf(response.version().toString()));
            body(body);
        }

        /**
         * Return a new {@link HttpResponse.Builder} that will inherit properties from this response.
         * This allows a new response to be made that will be identical to this one except for the properties
         * overridden by the builder methods.
         *
         * @param response a response
         * @return new builder based on the given response
         */
        public static Builder newBuilder(HttpResponse response) {
            return new Builder(response);
        }

        /**
         * Creates a builder with status set to 200 OK.
         *
         * @return {@code this}
         */
        public static Builder response() {
            return response(OK);
        }

        /**
         * Creates a builder with a given HTTP status code.
         *
         * @param status status code
         * @return {@code this}
         */
        public static Builder response(HttpResponseStatus status) {
            return new Builder(status);
        }

        /**
         * Sets the request that this response is responding to.
         *
         * @param request a request
         * @return {@code this}
         */
        public Builder request(HttpRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets HTTP response status code.
         *
         * @param status status code
         * @return {@code this}
         */
        public Builder status(HttpResponseStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Adds the necessary header to make the current request not cache-able by the client.
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
                    .ifPresent(cookie -> cookies.remove(cookie));

            return this;
        }

        /**
         * Sets the 'Content-Type' header to the specified {@code contentType}.
         *
         * @param contentType the content type to set
         * @return {@code this}
         */
        public Builder contentType(MediaType contentType) {
            header(CONTENT_TYPE, contentType.toString());
            return this;
        }

        /**
         * Sets an empty body on this response.
         *
         * @return {@code this}
         */
        public Builder removeBody() {
            return body(body.content()
                    .doOnNext(ReferenceCountUtil::release)
                    .ignoreElements()
            );
        }

        /**
         * Throws an exception if there are multiple content-length or if the content-length is not an integer.
         *
         * @return {@code this}
         */
        public Builder validateContentLength() {
            ensureContentLengthIsValid();

            return this;
        }

        /**
         * Builds a response.
         *
         * @return a response
         */
        public HttpResponse build() {
            return new HttpResponse(this);
        }
    }
}
