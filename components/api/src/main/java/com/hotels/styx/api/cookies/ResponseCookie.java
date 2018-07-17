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
package com.hotels.styx.api.cookies;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.hotels.styx.api.HttpCookieAttribute;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.HttpResponse;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Sets.newHashSet;
import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.common.Strings.quote;
import static io.netty.handler.codec.http.cookie.Cookie.UNDEFINED_MAX_AGE;
import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * Represents an HTTP cookie as sent in the {@code Set-Cookie} header.
 */
public final class ResponseCookie {
    private static final Joiner JOINER_ON_SEMI_COLON_AND_SPACE = Joiner.on("; ");

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
        this.hashCode = Objects.hashCode(name, value, domain, maxAge, path, secure, httpOnly);
    }

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name       cookie name
     * @param value      cookie value
     * @param attributes cookie attributes
     * @return a cookie
     */
    public static ResponseCookie cookie(String name, String value, HttpCookieAttribute... attributes) {
        return cookie(name, value, nonNulls(attributes));
    }

    // throws exception if any values are null
    private static <X> Set<X> nonNulls(X... array) {
        for (X item : array) {
            checkNotNull(item);
        }

        return newHashSet(array);
    }

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name       cookie name
     * @param value      cookie value
     * @param attributes cookie attributes
     * @return a cookie
     */
    public static ResponseCookie cookie(String name, String value, Iterable<HttpCookieAttribute> attributes) {
        ResponseCookie.Builder builder = new ResponseCookie.Builder(name, value);

        attributes.forEach(attribute -> {
            switch (attribute.name()) {
                case "Domain":
                    builder.domain(attribute.value());
                    break;
                case "Path":
                    builder.path(attribute.value());
                    break;
                case "Max-Age":
                    builder.maxAge(parseLong(attribute.value()));
                    break;
                case "Secure":
                    builder.secure(true);
                    break;
                case "HttpOnly":
                    builder.httpOnly(true);
                    break;
            }
        });

        return builder.build();
    }

    public static PseudoMap<String, ResponseCookie> decode(HttpHeaders headers) {
        return wrap(headers.getAll(SET_COOKIE).stream()
                .map(ClientCookieDecoder.LAX::decode)
                .map(ResponseCookie::convert)
                .collect(toSet()));
    }

    private static PseudoMap<String, ResponseCookie> wrap(Set<ResponseCookie> cookies) {
        return new PseudoMap<>(cookies, (name, cookie) -> cookie.name().equals(name));
    }

    public static void encode(HttpHeaders.Builder headers, Collection<ResponseCookie> cookies) {
        Set<Cookie> nettyCookies = cookies.stream()
                .map(ResponseCookie::convert)
                .collect(toSet());

        headers.set(SET_COOKIE, ServerCookieEncoder.LAX.encode(nettyCookies));
    }

    public static void encode(HttpResponse.Builder builder, ResponseCookie cookie) {
        builder.addHeader(SET_COOKIE, ServerCookieEncoder.LAX.encode(convert(cookie)));
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
        Iterable<HttpCookieAttribute> attributes = new ArrayList<HttpCookieAttribute>() {
            {
                if (!isNullOrEmpty(cookie.domain())) {
                    add(HttpCookieAttribute.domain(cookie.domain()));
                }
                if (cookie.maxAge() != Long.MIN_VALUE) {
                    add(HttpCookieAttribute.maxAge((int) cookie.maxAge()));
                }
                if (!isNullOrEmpty(cookie.path())) {
                    add(HttpCookieAttribute.path(cookie.path()));
                }
                if (cookie.isHttpOnly()) {
                    add(HttpCookieAttribute.httpOnly());
                }
                if (cookie.isSecure()) {
                    add(HttpCookieAttribute.secure());
                }
            }
        };
        String value = cookie.wrap() ? quote(cookie.value()) : cookie.value();
        return cookie(cookie.name(), value, attributes);
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
     * Returns cookie attributes.
     *
     * @return cookie attributes
     */
    // TODO delete after clean-up
    public Set<HttpCookieAttribute> attributes() {
        Set<HttpCookieAttribute> attributes = new HashSet<>();

        if (!isNullOrEmpty(domain)) {
            attributes.add(HttpCookieAttribute.domain(domain));
        }

        if (!isNullOrEmpty(path)) {
            attributes.add(HttpCookieAttribute.path(path));
        }

        if (maxAge != null && maxAge != UNDEFINED_MAX_AGE) {
            attributes.add(HttpCookieAttribute.maxAge(maxAge.intValue()));
        }

        if (secure) {
            attributes.add(HttpCookieAttribute.secure());
        }

        if (httpOnly) {
            attributes.add(HttpCookieAttribute.httpOnly());
        }

        return attributes;
    }


    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ResponseCookie other = (ResponseCookie) obj;
        return Objects.equal(name, other.name) && Objects.equal(value, other.value) && Objects.equal(attributes(), other.attributes());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(name)
                .append('=')
                .append(value)
                .append(isEmpty(attributes()) ? "" : "; ");

        return JOINER_ON_SEMI_COLON_AND_SPACE.appendTo(builder, attributes()).toString();
    }

    public Optional<Long> maxAge() {
        return Optional.ofNullable(maxAge).filter(value -> value != UNDEFINED_MAX_AGE);
    }

    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    public boolean httpOnly() {
        return httpOnly;
    }

    public Optional<String> domain() {
        return Optional.ofNullable(domain);
    }

    public boolean secure() {
        return secure;
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

        public Builder(String name, String value) {
            this.name = requireNonNull(name);
            this.value = requireNonNull(value);
        }

        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        public Builder value(String value) {
            this.value = requireNonNull(value);
            return this;
        }

        public Builder domain(String domain) {
            this.domain = requireNonNull(domain);
            return this;
        }

        public Builder maxAge(long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public Builder path(String path) {
            this.path = requireNonNull(path);
            return this;
        }

        public Builder httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public ResponseCookie build() {
            return new ResponseCookie(this);
        }
    }

}
