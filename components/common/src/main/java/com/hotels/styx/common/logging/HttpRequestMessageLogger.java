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
package com.hotels.styx.common.logging;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.Origin;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Logs client side requests and responses when enabled. Disabled by default.
 */
public class HttpRequestMessageLogger {
    private final Logger logger;
    private final boolean longFormatEnabled;

    public HttpRequestMessageLogger(String name, boolean longFormatEnabled) {
        this.longFormatEnabled = longFormatEnabled;
        logger = getLogger(name);
    }

    public void logRequest(HttpRequest request, Origin origin) {
        if (request == null) {
            logger.warn("requestId=N/A, request=null, origin={}", origin);
        } else {
            logger.info("requestId={}, request={}", request.id(), information(request, origin, longFormatEnabled));
        }
    }

    public void logResponse(HttpRequest request, HttpResponse response) {
        if (response == null) {
            logger.warn("requestId={}, response=null", id(request));
        } else {
            logger.info("requestId={}, response={}", id(request), information(response, longFormatEnabled));
        }
    }

    private static Object id(HttpRequest request) {
        return request != null ? request.id() : null;
    }

    private static Info information(HttpResponse response, boolean longFormatEnabled) {
        Info info = new Info().add("status", response.status());

        if (longFormatEnabled) {
            info.add("headers", response.headers());
        }
        return info;
    }

    private static Info information(HttpRequest request, Origin origin, boolean longFormatEnabled) {
        Info info = new Info()
                .add("method", request.method())
                .add("secure", request.isSecure())
                .add("uri", request.url())
                .add("origin", origin != null ? origin.hostAsString() : "N/A");

        if (longFormatEnabled) {
            info.add("headers", request.headers());
        }
        return info;
    }

    private static class Info {
        private final StringBuilder sb = new StringBuilder();

        public Info add(String variable, Object value) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(variable).append("=");

            if (value instanceof String) {
                sb.append('"').append(value).append('"');
            } else {
                sb.append(value);
            }

            return this;
        }

        @Override
        public String toString() {
            return "{" + sb + "}";
        }
    }
}
