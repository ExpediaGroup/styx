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
package com.hotels.styx.client.applications.metrics;

import static java.lang.String.valueOf;

/**
 * Provides methods relating to HTTP status codes.
 */
final class StatusCodes {
    private StatusCodes() {
    }

    /**
     * Returns the metric name for this status code.
     *
     * @param statusCode status code
     * @return the metric name for this status code
     */
    static String statusCodeName(int statusCode) {
        return "status." + valueOf(sanitizeStatusCode(statusCode));
    }

    private static int sanitizeStatusCode(int statusCode) {
        if (statusCode < 100 || statusCode >= 600) {
            return -1;
        }
        return statusCode;
    }
}
