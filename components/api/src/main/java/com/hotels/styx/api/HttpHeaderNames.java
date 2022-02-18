/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import io.netty.util.AsciiString;

/**
 * Provides optimized constants for the standard HTTP header names.
 */
public final class HttpHeaderNames {
    public static final CharSequence USER_AGENT = io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
    public static final CharSequence CONTENT_TYPE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
    public static final CharSequence CONTENT_LENGTH = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
    public static final CharSequence COOKIE = io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
    public static final CharSequence LOCATION = io.netty.handler.codec.http.HttpHeaderNames.LOCATION;

    public static final CharSequence CONNECTION = io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
    public static final CharSequence HOST = io.netty.handler.codec.http.HttpHeaderNames.HOST;
    public static final CharSequence DATE = io.netty.handler.codec.http.HttpHeaderNames.DATE;
    public static final CharSequence EXPECT = io.netty.handler.codec.http.HttpHeaderNames.EXPECT;
    public static final CharSequence CONTINUE = io.netty.handler.codec.http.HttpHeaderValues.CONTINUE;
    public static final CharSequence TRANSFER_ENCODING = io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
    public static final CharSequence CHUNKED = io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;

    public static final CharSequence SET_COOKIE = io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
    public static final CharSequence X_FORWARDED_FOR = new AsciiString("X-Forwarded-For");
    public static final CharSequence X_FORWARDED_PROTO = new AsciiString("X-Forwarded-Proto");

    public static final CharSequence KEEP_ALIVE = io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
    public static final CharSequence PROXY_AUTHENTICATE = io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHENTICATE;
    public static final CharSequence PROXY_AUTHORIZATION = io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;

    public static final CharSequence TE = io.netty.handler.codec.http.HttpHeaderNames.TE;
    public static final CharSequence TRAILER = io.netty.handler.codec.http.HttpHeaderNames.TRAILER;
    public static final CharSequence UPGRADE = io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
    public static final CharSequence VIA = io.netty.handler.codec.http.HttpHeaderNames.VIA;
    public static final CharSequence CACHE_CONTROL = io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;

    // note: the other constants should also be migrated away from deprecated methods and classes.
    public static final CharSequence CONTENT_LANGUAGE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LANGUAGE;

    private HttpHeaderNames() {
    }
}
