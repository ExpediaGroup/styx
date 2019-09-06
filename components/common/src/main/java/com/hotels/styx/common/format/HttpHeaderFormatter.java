/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.common.format;

import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.RequestCookie;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public class HttpHeaderFormatter {

    private static final List<String> COOKIE_HEADER_NAMES = Arrays.asList("cookie", "set-cookie");

    private List<String> headerValuesToHide;
    private List<String> cookieValuesToHide;

    public HttpHeaderFormatter(List<String> headerValuesToHide, List<String> cookieValuesToHide) {
        this.headerValuesToHide = requireNonNull(headerValuesToHide);
        this.cookieValuesToHide = requireNonNull(cookieValuesToHide);
    }

    public String format(HttpHeaders headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(this::hideOrFormatHeader)
                .collect(Collectors.joining(", "));
    }

    private String hideOrFormatHeader(HttpHeader header) {
        return shouldHideHeader(header)
                ? header.name() + ":****"
                : formatHeaderAsCookieIfNecessary(header);
    }

    private boolean shouldHideHeader(HttpHeader header) {
        return headerValuesToHide.stream()
                .anyMatch(h -> h.equalsIgnoreCase(header.name()));
    }

    private String formatHeaderAsCookieIfNecessary(HttpHeader header) {
        return isHeaderACookie(header)
                ? formatCookieHeader(header)
                : header.toString();
    }

    private boolean isHeaderACookie(HttpHeader header) {
        return COOKIE_HEADER_NAMES.contains(header.name().toLowerCase());
    }

    private String formatCookieHeader(HttpHeader header) {
        String cookies = RequestCookie.decode(header.value()).stream()
                .map(this::hideOrFormatCookie)
                .collect(Collectors.joining(";"));

        return header.name() + ":" + cookies;
    }

    private String hideOrFormatCookie(RequestCookie cookie) {
        return shouldHideCookie(cookie)
                ? cookie.name() + "=****"
                : cookie.toString();
    }

    private boolean shouldHideCookie(RequestCookie cookie) {
        return cookieValuesToHide.stream()
                .anyMatch(h -> h.equalsIgnoreCase(cookie.name()));
    }

}
