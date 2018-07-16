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

import com.google.common.base.Objects;
import com.hotels.styx.api.HttpHeaders;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.styx.api.HttpHeaderNames.COOKIE;
import static com.hotels.styx.api.common.Strings.quote;
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
        checkNotNull(value, "value cannot be null");
        this.name = name;
        this.value = value;
        this.hashCode = Objects.hashCode(name, value);
    }

    /**
     * Constructs a cookie with a name, value and attributes.
     *
     * @param name  cookie name
     * @param value cookie value
     * @return a cookie
     */
    public static RequestCookie cookie(String name, String value) {
        return new RequestCookie(name, value);
    }

    public static PseudoMap<String, RequestCookie> decode(HttpHeaders headers) {
        return wrap(headers.getAll(COOKIE).stream()
                .map(ServerCookieDecoder.LAX::decode)
                .flatMap(Collection::stream)
                .map(RequestCookie::convert)
                .collect(toSet()));
    }

    private static PseudoMap<String, RequestCookie> wrap(Set<RequestCookie> cookies) {
        return new PseudoMap<>(cookies, (name, cookie) -> cookie.name().equals(name));
    }

    public static void encode(HttpHeaders.Builder headers, Collection<RequestCookie> cookies) {
        Set<Cookie> nettyCookies = cookies.stream()
                .map(RequestCookie::convert)
                .collect(toSet());

        if (!nettyCookies.isEmpty()) {
            headers.set(COOKIE, ClientCookieEncoder.LAX.encode(nettyCookies));
        }
    }

    private static Cookie convert(RequestCookie cookie) {
        return new DefaultCookie(cookie.name, cookie.value);
    }

    private static RequestCookie convert(Cookie nettyCookie) {
        String name = nettyCookie.name();
        String value = nettyCookie.wrap() ? quote(nettyCookie.value()) : nettyCookie.value();

        return cookie(name, value);
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
        return Objects.equal(name, other.name) && Objects.equal(value, other.value);
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
