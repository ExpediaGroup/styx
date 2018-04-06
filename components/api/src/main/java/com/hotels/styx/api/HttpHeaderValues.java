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

import io.netty.handler.codec.http.HttpHeaders.Values;

import static io.netty.handler.codec.http.HttpHeaders.newEntity;

/**
 * Provides optimized constants for the standard HTTP header values.
 */
public final class HttpHeaderValues {
    public static final CharSequence PLAIN_TEXT = newEntity("text/plain; charset=utf-8");
    public static final CharSequence HTML = newEntity("text/html; charset=UTF-8");
    public static final CharSequence APPLICATION_JSON = newEntity("application/json; charset=utf-8");
    public static final CharSequence CLOSE = newEntity(Values.CLOSE);
    public static final CharSequence KEEP_ALIVE = newEntity(Values.KEEP_ALIVE);
    public static final CharSequence CHUNKED = newEntity(Values.CHUNKED);

    private HttpHeaderValues() {
    }
}
