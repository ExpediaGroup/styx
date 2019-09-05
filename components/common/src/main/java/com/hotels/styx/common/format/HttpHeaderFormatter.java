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

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class HttpHeaderFormatter {

    private static final List<String> COOKIE_HEADER_NAMES = Arrays.asList("cookie", "set-cookie");
    private static HttpHeaderFormatter instance;

    public static void initialise(List<String> headerValuesToHide, List<String> cookieValuesToHide) {
        headerValuesToHide.replaceAll(String::toLowerCase);
        cookieValuesToHide.replaceAll(String::toLowerCase);
        instance = new HttpHeaderFormatter(headerValuesToHide, cookieValuesToHide);
    }

    public static HttpHeaderFormatter instance() {
        return instance == null
                ? new HttpHeaderFormatter(emptyList(), emptyList())
                : instance;
    }

    private List<String> headerValuesToHide;
    private List<String> cookieValuesToHide;

    private HttpHeaderFormatter(List<String> headerValuesToHide, List<String> cookieValuesToHide) {
        this.headerValuesToHide = requireNonNull(headerValuesToHide);
        this.cookieValuesToHide = requireNonNull(cookieValuesToHide);
    }

    public String format(HttpHeaders headers) {
        String sanitisedHeaders = StreamSupport.stream(headers.spliterator(), false)
                .map(this::hideOrFormatHeader)
                .collect(Collectors.joining(", "));

        return "[" + sanitisedHeaders + "]";
    }

    private String hideOrFormatHeader(HttpHeader header) {
        return headerValuesToHide.contains(header.name().toLowerCase())
                ? header.name() + ":****"
                : formatHeaderAsCookieIfNecessary(header);
    }

    private String formatHeaderAsCookieIfNecessary(HttpHeader header) {
        return COOKIE_HEADER_NAMES.contains(header.name().toLowerCase())
                ? formatCookieHeader(header)
                : header.toString();
    }

    private String formatCookieHeader(HttpHeader header) {
        String cookies = RequestCookie.decode(header.value()).stream()
                .map(this::hideOrFormatCookie)
                .collect(Collectors.joining(";"));

        return header.name() + ":" + cookies;
    }

    private String hideOrFormatCookie(RequestCookie cookie) {
        return cookieValuesToHide.contains(cookie.name().toLowerCase())
                ? cookie.name() + "=****"
                : cookie.toString();
    }

}
