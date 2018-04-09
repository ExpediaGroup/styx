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

import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;

import static io.netty.handler.codec.http.HttpHeaders.newEntity;

/**
 * Provides optimized constants for the standard HTTP header names.
 */
public final class HttpHeaderNames {
    public static final CharSequence USER_AGENT = newEntity(Names.USER_AGENT);
    public static final CharSequence CONTENT_TYPE = newEntity(Names.CONTENT_TYPE);
    public static final CharSequence CONTENT_LENGTH = newEntity(Names.CONTENT_LENGTH);
    public static final CharSequence COOKIE = newEntity(Names.COOKIE);
    public static final CharSequence LOCATION = newEntity(Names.LOCATION);

    public static final CharSequence CONNECTION = newEntity(Names.CONNECTION);
    public static final CharSequence HOST = newEntity(Names.HOST);
    public static final CharSequence DATE = newEntity(Names.DATE);
    public static final CharSequence EXPECT = newEntity(Names.EXPECT);
    public static final CharSequence CONTINUE = newEntity(Values.CONTINUE);
    public static final CharSequence TRANSFER_ENCODING = newEntity(Names.TRANSFER_ENCODING);
    public static final CharSequence CHUNKED = newEntity(Values.CHUNKED);

    public static final CharSequence SET_COOKIE = newEntity(Names.SET_COOKIE);
    public static final CharSequence X_FORWARDED_FOR = newEntity("X-Forwarded-For");
    public static final CharSequence X_FORWARDED_PROTO = newEntity("X-Forwarded-Proto");

    public static final CharSequence KEEP_ALIVE = newEntity(Values.KEEP_ALIVE);
    public static final CharSequence PROXY_AUTHENTICATE = newEntity(Names.PROXY_AUTHENTICATE);
    public static final CharSequence PROXY_AUTHORIZATION = newEntity(Names.PROXY_AUTHORIZATION);

    public static final CharSequence TE = newEntity(Names.TE);
    public static final CharSequence TRAILER = newEntity(Names.TRAILER);
    public static final CharSequence UPGRADE = newEntity(Names.UPGRADE);
    public static final CharSequence VIA = newEntity(Names.VIA);

    private HttpHeaderNames() {
    }
}
