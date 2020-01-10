/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.cookie.Cookie.UNDEFINED_MAX_AGE;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * Represents an HTTP cookie as sent in the HTTP response {@code Set-Cookie} header.
 *
 * A server can include a {@code ResponseCookie} in its response to a client request.
 * It contains cookie {@code name}, {@code value}, and attributes such as {@code path}
 * and {@code maxAge}.
 */
public final class ResponseCookie {
    private final String name;
    private final String value;

    // attributes
    private final String domain;
    private final Long maxAge;
    private final String path;
    private final boolean httpOnly;
    private final boolean secure;
    private final int hashCode;

    private ResponseCookie(Builder builder) {
        if (builder.name == null || builder.name.isEmpty()) {
            throw new IllegalArgumentException();
        }

        this.name = builder.name;
        this.value = builder.value;

        this.domain = builder.domain;
        this.maxAge = builder.maxAge;
        this.path = builder.path;
        this.httpOnly = builder.httpOnly;
        this.secure = builder.secure;
        this.hashCode = hash(name, value, domain, maxAge, path, secure, httpOnly);
    }

    /**
     * Creates a builder for response cookie.
     *
     * @param name  name
     * @param value value
     * @return builder
     */
    public static ResponseCookie.Builder responseCookie(String name, String value) {
        return new ResponseCookie.Builder(name, value);
    }

    /**
     * Decodes a list of "Set-Cookie" header values into a set of {@link ResponseCookie} objects.
     *
     * @param headerValues "Set-Cookie" header values
     * @return cookies
     */
    public static Set<ResponseCookie> decode(List<String> headerValues) {
        return headerValues.stream()
                .map(ClientCookieDecoder.LAX::decode)
                .filter(Objects::nonNull)
                .map(ResponseCookie::convert)
                .collect(Collectors.toSet());
    }

    /**
     * Encodes a collection of {@link ResponseCookie} objects into a list of "Set-Cookie" header values.
     *
     * @param cookies cookies
     * @return "Set-Cookie" header values
     */
    public static List<String> encode(Collection<ResponseCookie> cookies) {
        Set<Cookie> nettyCookies = cookies.stream()
                .map(ResponseCookie::convert)
                .collect(toSet());

        return ServerCookieEncoder.LAX.encode(nettyCookies);
    }

    /**
     * Encodes a {@link ResponseCookie} object into a "Set-Cookie" header value.
     *
     * @param cookie cookie
     * @return "Set-Cookie" header value
     */
    public static String encode(ResponseCookie cookie) {
        return ServerCookieEncoder.LAX.encode(convert(cookie));
    }

    /**
     * Returns cookie name.
     *
     * @return cookie name
     */
    public String name() {
        return name;
    }

    /**
     * Returns cookie value.
     *
     * @return cookie value
     */
    public String value() {
        return value;
    }

    /**
     * Returns the Max-Age attribute, if present.
     *
     * @return Max-Age attribute, if present
     */
    public Optional<Long> maxAge() {
        return Optional.ofNullable(maxAge).filter(value -> value != UNDEFINED_MAX_AGE);
    }

    /**
     * Returns the Path attribute, if present.
     *
     * @return Path attribute, if present
     */
    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    /**
     * Returns true if the HttpOnly attribute is present.
     *
     * @return true if the HttpOnly attribute is present
     */
    public boolean httpOnly() {
        return httpOnly;
    }

    /**
     * Returns the Domain attribute, if present.
     *
     * @return Domain attribute, if present
     */
    public Optional<String> domain() {
        return Optional.ofNullable(domain);
    }

    /**
     * Returns true if the Secure attribute is present.
     *
     * @return true if the Secure attribute is present
     */
    public boolean secure() {
        return secure;
    }

    private static Cookie convert(ResponseCookie cookie) {
        DefaultCookie nCookie = new DefaultCookie(cookie.name, cookie.value);

        nCookie.setDomain(cookie.domain);
        nCookie.setHttpOnly(cookie.httpOnly);
        nCookie.setSecure(cookie.secure);
        if (cookie.maxAge != null) {
            nCookie.setMaxAge(cookie.maxAge);
        }
        nCookie.setPath(cookie.path);

        return nCookie;
    }

    private static ResponseCookie convert(Cookie cookie) {
        String value = cookie.wrap() ? quote(cookie.value()) : cookie.value();

        return responseCookie(cookie.name(), value)
                .domain(cookie.domain())
                .path(cookie.path())
                .maxAge(cookie.maxAge())
                .httpOnly(cookie.isHttpOnly())
                .secure(cookie.isSecure())
                .build();
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResponseCookie that = (ResponseCookie) o;
        return httpOnly == that.httpOnly
                && secure == that.secure
                && Objects.equals(name, that.name)
                && Objects.equals(value, that.value)
                && Objects.equals(domain, that.domain)
                && Objects.equals(maxAge, that.maxAge)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "ResponseCookie{"
                + "name='" + name + '\''
                + ", value='" + value + '\''
                + ", domain='" + domain + '\''
                + ", maxAge=" + maxAge
                + ", path='" + path + '\''
                + ", httpOnly=" + httpOnly
                + ", secure=" + secure
                + '}';
    }

    /**
     * Builds response cookie.
     */
    public static class Builder {
        private String name;
        private String value;

        // attributes
        private String domain;
        private Long maxAge;
        private String path;
        private boolean httpOnly;
        private boolean secure;

        private Builder(String name, String value) {
            this.name = requireNonNull(name);
            this.value = requireNonNull(value);
        }

        /**
         * Sets the cookie name.
         *
         * @param name cookie name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        /**
         * Sets the cookie value.
         *
         * @param value cookie value
         * @return this builder
         */
        public Builder value(String value) {
            this.value = requireNonNull(value);
            return this;
        }

        /**
         * Sets the Domain attribute.
         *
         * @param domain Domain attribute
         * @return this builder
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets the Max-Age attribute.
         *
         * @param maxAge Max-Age attribute
         * @return this builder
         */
        public Builder maxAge(long maxAge) {
            this.maxAge = maxAge == UNDEFINED_MAX_AGE ? null : maxAge;
            return this;
        }

        /**
         * Sets the Path attribute.
         *
         * @param path Path attribute
         * @return this builder
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets/unsets the HttpOnly attribute.
         *
         * @param httpOnly true to set the attribute, false to unset it
         * @return this builder
         */
        public Builder httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        /**
         * Sets/unsets the Secure attribute.
         *
         * @param secure true to set the attribute, false to unset it
         * @return this builder
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public ResponseCookie build() {
            return new ResponseCookie(this);
        }
    }

}
