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

import com.hotels.styx.api.HttpVersion;

import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CHUNKED;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CLOSE;
import static com.hotels.styx.api.HttpHeaderValues.KEEP_ALIVE;

/**
 * Contains static methods for inspecting different properties of HTTP requests.
 */
public final class HttpMessageSupport {
    private HttpMessageSupport() {
    }

    public static boolean chunked(HttpHeaders headers) {
        for (String value : headers.getAll(TRANSFER_ENCODING)) {
            if (value.equalsIgnoreCase(CHUNKED.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean keepAlive(HttpHeaders headers, HttpVersion version) {
        Optional<String> connection = headers.get(CONNECTION);

        if (connection.isPresent()) {
            if (CLOSE.toString().equalsIgnoreCase(connection.get())) {
                return false;
            }
            if (KEEP_ALIVE.toString().equalsIgnoreCase(connection.get())) {
                return true;
            }
        }
        return version.isKeepAliveDefault();
    }
}

