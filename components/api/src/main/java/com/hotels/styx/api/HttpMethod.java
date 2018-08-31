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

import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Provides constants for HTTP Methods.
 */
public final class HttpMethod {
    public static final HttpMethod OPTIONS = new HttpMethod("OPTIONS");
    public static final HttpMethod GET = new HttpMethod("GET");
    public static final HttpMethod HEAD = new HttpMethod("HEAD");
    public static final HttpMethod POST = new HttpMethod("POST");
    public static final HttpMethod PUT = new HttpMethod("PUT");
    public static final HttpMethod PATCH = new HttpMethod("PATCH");
    public static final HttpMethod DELETE = new HttpMethod("DELETE");
    public static final HttpMethod TRACE = new HttpMethod("TRACE");
    public static final HttpMethod CONNECT = new HttpMethod("CONNECT");

    public static final Set<HttpMethod> METHODS = ImmutableSet.of(
            OPTIONS,
            GET,
            HEAD,
            POST,
            PUT,
            PATCH,
            DELETE,
            TRACE,
            CONNECT
    );

    private static final Map<String, HttpMethod> METHODS_BY_NAME =
            METHODS.stream().collect(toMap(HttpMethod::name, identity()));

    private final String name;

    private HttpMethod(String name) {
        this.name = name;
    }

    public static HttpMethod httpMethod(String name) {
        checkArgument(METHODS_BY_NAME.containsKey(name), "No such HTTP method %s", name);

        return METHODS_BY_NAME.get(name);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
