/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.debug;

import com.hotels.styx.api.LiveHttpRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides a static context to keep a list of the requests we want to debug.
 */
public class RequestDebugger {

    private static Set<String> requestIdPrefixes = new HashSet<>();

    public static void add(String requestIdPrefix) {
        if (requestIdPrefix != null) {
            requestIdPrefixes.add(requestIdPrefix);
        }
    }

    public static boolean shouldDebugRequest(LiveHttpRequest request) {
        String requestId = request.id().toString();
        String requestPrefix = requestId.substring(0, requestId.indexOf('-'));
        return requestIdPrefixes.contains(requestPrefix);
    }
}

