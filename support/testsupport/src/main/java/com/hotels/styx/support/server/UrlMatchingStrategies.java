/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.support.server;

import com.github.tomakehurst.wiremock.matching.UrlPattern;

import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

public final class UrlMatchingStrategies {
    private UrlMatchingStrategies() {
    }

    public static UrlPattern urlStartingWith(String url) {
        return urlMatching(url + ".*");
    }

    public static UrlPattern urlEndingWith(String url) {
        return urlMatching(".*" + url);
    }
}
