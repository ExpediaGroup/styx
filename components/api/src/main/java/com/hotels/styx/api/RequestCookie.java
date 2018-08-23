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


import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * Represents an HTTP cookie as sent in the {@code Cookie} header of an HTTP request.
 */
public final class RequestCookie {
    private final String name;
    private final String value;
    private final int hashCode;

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name  cookie name
     * @param value cookie value
     */
    private RequestCookie(String name, String value) {
        checkArgument(!isNullOrEmpty(name), "name cannot be null or empty");
        requireNonNull(value, "value cannot be null");
        this.name = name;
        this.value = value;
        this.hashCode = Objects.hash(name, value);
    }

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name  cookie name
     * @param value cookie value
     * @return a cookie
     */
    public static RequestCookie requestCookie(String name, String value) {
        return new RequestCookie(name, value);
    }

    /**
     * Decodes a "Cookie" header value into a set of {@link RequestCookie} objects.
     *
     * @param headerValue "Cookie" header value
     * @return cookies
     */
    public static Set<RequestCookie> decode(String headerValue) {
        if (headerValue == null) {
            return emptySet();
        }

        return ServerCookieDecoder.LAX.decode(headerValue).stream()
                .map(RequestCookie::convert)
                .collect(toSet());
    }

    /**
     * Encodes a collection of {@link RequestCookie} objects into a "Cookie" header value.
     *
     * @param cookies cookies
     * @return "Cookie" header value
     */
    public static String encode(Collection<RequestCookie> cookies) {
        checkArgument(!cookies.isEmpty(), "Cannot create cookie header value from zero cookies");

        Set<Cookie> nettyCookies = cookies.stream()
                .map(RequestCookie::convert)
                .collect(toSet());

        return ClientCookieEncoder.LAX.encode(nettyCookies);
    }

    private static Cookie convert(RequestCookie cookie) {
        return new DefaultCookie(cookie.name, cookie.value);
    }

    private static RequestCookie convert(Cookie nettyCookie) {
        String name = nettyCookie.name();
        String value = nettyCookie.wrap() ? quote(nettyCookie.value()) : nettyCookie.value();

        return requestCookie(name, value);
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
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
        RequestCookie other = (RequestCookie) obj;
        return Objects.equals(name, other.name) && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
