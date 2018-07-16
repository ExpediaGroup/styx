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
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.httpOnly;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;
import static com.hotels.styx.api.HttpCookieAttribute.secure;
import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.common.Strings.quote;
import static java.util.stream.Collectors.toSet;

/**
 * Represents an HTTP cookie as sent in the {@code Set-Cookie} header.
 */
public final class ResponseCookie {
    private static final Joiner JOINER_ON_SEMI_COLON_AND_SPACE = Joiner.on("; ");
    private final String name;
    private final String value;
    private final int hashCode;
    private final Iterable<HttpCookieAttribute> attributes;

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name       cookie name
     * @param value      cookie value
     * @param attributes cookie attributes
     */
    private ResponseCookie(String name, String value, Iterable<HttpCookieAttribute> attributes) {
        checkArgument(!isNullOrEmpty(name), "name cannot be null or empty");
        checkNotNull(value, "value cannot be null");
        this.name = name;
        this.value = value;
        this.attributes = checkNotNull(attributes);
        this.hashCode = Objects.hashCode(name, value, attributes);
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
        return new ResponseCookie(name, value, nonNulls(attributes));
    }

    // throws exception if any values are null
    private static <X> Collection<X> nonNulls(X... array) {
        for (X item : array) {
            checkNotNull(item);
        }

        return newArrayList(array);
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
        return new ResponseCookie(name, value, attributes);
    }

    public static Set<ResponseCookie> decode(HttpHeaders headers) {
        return headers.getAll(SET_COOKIE).stream()
                .map(ClientCookieDecoder.LAX::decode)
                .map(ResponseCookie::convert)
                .collect(toSet());
    }

    public static void encode(HttpHeaders.Builder headers, Collection<ResponseCookie> cookies) {
        Set<Cookie> nettyCookies = cookies.stream()
                .map(ResponseCookie::convert)
                .collect(toSet());

        headers.set(SET_COOKIE, ServerCookieEncoder.LAX.encode(nettyCookies));
    }

    private static Cookie convert(ResponseCookie cookie) {
        return new DefaultCookie(cookie.name, cookie.value);
    }

    private static ResponseCookie convert(Cookie cookie) {
        Iterable<HttpCookieAttribute> attributes = new ArrayList<HttpCookieAttribute>() {
            {
                if (!isNullOrEmpty(cookie.domain())) {
                    add(domain(cookie.domain()));
                }
                if (!isNullOrEmpty(cookie.path())) {
                    add(path(cookie.path()));
                }
                if (cookie.maxAge() != Long.MIN_VALUE) {
                    add(maxAge((int) cookie.maxAge()));
                }
                if (cookie.isHttpOnly()) {
                    add(httpOnly());
                }
                if (cookie.isSecure()) {
                    add(secure());
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
    public Iterable<HttpCookieAttribute> attributes() {
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
        return Objects.equal(name, other.name) && Objects.equal(value, other.value) && Objects.equal(attributes, other.attributes);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append(name)
                .append('=')
                .append(value)
                .append(isEmpty(attributes) ? "" : "; ");

        return JOINER_ON_SEMI_COLON_AND_SPACE.appendTo(builder, attributes).toString();
    }
}
