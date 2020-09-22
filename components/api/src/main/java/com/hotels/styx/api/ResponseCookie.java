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
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.cookie.Cookie.UNDEFINED_MAX_AGE;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

/**
 * Represents an HTTP cookie as sent in the HTTP response {@code Set-Cookie} header.
 * <p>
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
    private final SameSite sameSite;

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
        this.sameSite = builder.sameSite;
        this.hashCode = hash(name, value, domain, maxAge, path, secure, httpOnly, sameSite);
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
     * Encodes this object into a "Set-Cookie" header value.
     *
     * @return "Set-Cookie" header value
     */
    String asSetCookieString() {
        return ServerCookieEncoder.LAX.encode(asDefaultCookie());
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

    /**
     * Returns the SameSite attribute, if present.
     *
     * @return SameSite attribute, if present
     */
    public Optional<SameSite> sameSite() {
        return Optional.ofNullable(sameSite);
    }

    private DefaultCookie asDefaultCookie() {
        DefaultCookie nettyCookie = new DefaultCookie(name, value);

        nettyCookie.setDomain(domain);
        nettyCookie.setHttpOnly(httpOnly);
        nettyCookie.setSecure(secure);
        if (maxAge != null) {
            nettyCookie.setMaxAge(maxAge);
        }
        nettyCookie.setPath(path);
        nettyCookie.setSameSite(sameSite);

        return nettyCookie;
    }

    private static DefaultCookie convert(ResponseCookie cookie) {
        DefaultCookie nettyCookie = new DefaultCookie(cookie.name, cookie.value);

        nettyCookie.setDomain(cookie.domain);
        nettyCookie.setHttpOnly(cookie.httpOnly);
        nettyCookie.setSecure(cookie.secure);
        if (cookie.maxAge != null) {
            nettyCookie.setMaxAge(cookie.maxAge);
        }
        nettyCookie.setPath(cookie.path);
        nettyCookie.setSameSite(cookie.sameSite);

        return nettyCookie;
    }

    private static ResponseCookie convert(Cookie cookie) {
        String value = cookie.wrap() ? quote(cookie.value()) : cookie.value();

        Builder builder = responseCookie(cookie.name(), value)
                .domain(cookie.domain())
                .path(cookie.path())
                .maxAge(cookie.maxAge())
                .httpOnly(cookie.isHttpOnly())
                .secure(cookie.isSecure());

        /* NOTE This DefaultCookie seems to be the only non-deprecated implementation of Cookie in netty,
                so this should always evaluate to true. */
        if (cookie instanceof DefaultCookie) {
            builder = builder.sameSite(((DefaultCookie) cookie).sameSite());
        }

        return builder.build();
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
                && Objects.equals(path, that.path)
                && Objects.equals(sameSite, that.sameSite);
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
                + ", sameSite=" + sameSite
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
        private SameSite sameSite;

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

        /**
         * Sets/unsets the SameSite attribute.
         *
         * @param sameSite enum with a valid value for the SameSite attribute
         * @return this builder
         */
        public Builder sameSite(SameSite sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        /**
         * Sets/unsets the SameSite attribute.
         *
         * @param sameSite SameSite attribute
         * @return this builder
         */
        public Builder sameSiteRawValue(String sameSite) {
            this.sameSite = SameSite.valueOf(sameSite);
            return this;
        }


        public ResponseCookie build() {
            return new ResponseCookie(this);
        }
    }

}
