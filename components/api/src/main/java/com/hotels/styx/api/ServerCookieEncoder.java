/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.hotels.styx.api;

import io.netty.handler.codec.http.HttpHeaderDateFormat;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieEncoder;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.hotels.styx.api.CookieUtil.add;
import static com.hotels.styx.api.CookieUtil.addQuoted;
import static com.hotels.styx.api.CookieUtil.stringBuilder;
import static com.hotels.styx.api.CookieUtil.stripTrailingSeparator;
import static java.util.Objects.requireNonNull;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used server side,
 * so some fields are sent (Version is typically ignored).
 *
 * As Netty's Cookie merges Expires and MaxAge into one single field, only Max-Age field is sent.
 *
 * Note that multiple cookies are supposed to be sent at once in a single "Set-Cookie" header.
 *
 * <pre>
 * // Example
 * {@link HttpRequest} req = ...;
 * res.setHeader("Cookie", {@link ServerCookieEncoder}.encode("JSESSIONID", "1234"));
 * </pre>
 *
 */
final class ServerCookieEncoder extends CookieEncoder {

    /**
     * Lax instance that doesn't validate name and value, and that allows multiple
     * cookies with the same name.
     */
    public static final ServerCookieEncoder LAX = new ServerCookieEncoder(false);

    private ServerCookieEncoder(boolean strict) {
        super(strict);
    }

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    public String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public String encode(Cookie cookie) {
        final String name = requireNonNull(cookie, "cookie").name();
        final String value = cookie.value() != null ? cookie.value() : "";

        validateCookie(name, value);

        StringBuilder buf = stringBuilder();

        if (cookie.wrap()) {
            addQuoted(buf, name, value);
        } else {
            add(buf, name, value);
        }

        if (cookie.maxAge() != Long.MIN_VALUE) {
            if (cookie.maxAge() >= 0) {
                add(buf, CookieHeaderNames.MAX_AGE, cookie.maxAge());
            }
            Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis());
            add(buf, CookieHeaderNames.EXPIRES, HttpHeaderDateFormat.get().format(expires));
        }

        if (cookie.path() != null) {
            add(buf, CookieHeaderNames.PATH, cookie.path());
        }

        if (cookie.domain() != null) {
            add(buf, CookieHeaderNames.DOMAIN, cookie.domain());
        }
        if (cookie.isSecure()) {
            add(buf, CookieHeaderNames.SECURE);
        }
        if (cookie.isHttpOnly()) {
            add(buf, CookieHeaderNames.HTTPONLY);
        }

        return stripTrailingSeparator(buf);
    }

    /** Deduplicate a list of encoded cookies by keeping only the last instance with a given name.
     *
     * @param encoded The list of encoded cookies.
     * @param nameToLastIndex A map from cookie name to index of last cookie instance.
     * @return The encoded list with all but the last instance of a named cookie.
     */
    private static List<String> deduplicate(List<String> encoded, Map<String, Integer> nameToLastIndex) {
        boolean[] isLastInstance = new boolean[encoded.size()];
        for (int idx : nameToLastIndex.values()) {
            isLastInstance[idx] = true;
        }
        List<String> dedupd = new ArrayList<>(nameToLastIndex.size());
        for (int i = 0, n = encoded.size(); i < n; i++) {
            if (isLastInstance[i]) {
                dedupd.add(encoded.get(i));
            }
        }
        return dedupd;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Cookie... cookies) {
        if (requireNonNull(cookies, "cookies").length == 0) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<>(cookies.length);
        Map<String, Integer> nameToIndex = strict && cookies.length > 1 ? new HashMap<>() : null;
        boolean hasDupdName = false;
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i) != null;
            }
        }
        return hasDupdName ? deduplicate(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Collection<? extends Cookie> cookies) {
        if (requireNonNull(cookies, "cookies").isEmpty()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<>(cookies.size());
        Map<String, Integer> nameToIndex = strict && cookies.size() > 1 ? new HashMap<>() : null;
        int i = 0;
        boolean hasDupdName = false;
        for (Cookie c : cookies) {
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? deduplicate(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = requireNonNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return Collections.emptyList();
        }

        List<String> encoded = new ArrayList<>();
        Cookie firstCookie = cookiesIt.next();
        Map<String, Integer> nameToIndex = strict && cookiesIt.hasNext() ? new HashMap<>() : null;
        int i = 0;
        encoded.add(encode(firstCookie));
        boolean hasDupdName = nameToIndex != null && nameToIndex.put(firstCookie.name(), i++) != null;
        while (cookiesIt.hasNext()) {
            Cookie c = cookiesIt.next();
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? deduplicate(encoded, nameToIndex) : encoded;
    }
}
