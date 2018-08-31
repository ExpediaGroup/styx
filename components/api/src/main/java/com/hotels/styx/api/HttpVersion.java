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

import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * HTTP Version.
 */
public class HttpVersion {
    public static final HttpVersion HTTP_1_0 = new HttpVersion("HTTP/1.0");
    public static final HttpVersion HTTP_1_1 = new HttpVersion("HTTP/1.1");

    private static final Map<String, HttpVersion> VERSIONS = Stream.of(HTTP_1_0, HTTP_1_1)
            .collect(toMap(HttpVersion::toString, identity()));

    private final String version;

    private HttpVersion(String version) {
        this.version = version;
    }

    public static HttpVersion httpVersion(String version) {
        checkArgument(VERSIONS.containsKey(version), "No such HTTP version %s", version);

        return VERSIONS.get(version);
    }

    @Override
    public String toString() {
        return version;
    }

    public boolean isKeepAliveDefault() {
        return this != HTTP_1_0;
    }
}
