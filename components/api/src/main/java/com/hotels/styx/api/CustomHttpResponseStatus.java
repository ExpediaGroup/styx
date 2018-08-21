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

/**
 * Custom HTTP response status codes.
 */
public final class CustomHttpResponseStatus {
    /**
     * 520 - Origin server timed out. This typically happens when the origin host is not serving pages. Please check your origin servers.
     */
    public static final HttpResponseStatus ORIGIN_SERVER_TIMED_OUT = new HttpResponseStatus(520, "Origin server timed out");

    /**
     * 521 - Indicates that the origin server refused the connection.
     */
    public static final HttpResponseStatus ORIGIN_CONNECTION_REFUSED = new HttpResponseStatus(521, "Origin refused the connection");

    /**
     * 522 - Indicated that a server connection timed out.
     */
    public static final HttpResponseStatus ORIGIN_CONNECTION_TIMED_OUT = new HttpResponseStatus(522, "Connection to origin timed out");

    private CustomHttpResponseStatus() {
    }
}
