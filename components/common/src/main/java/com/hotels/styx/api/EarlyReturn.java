/*
  Copyright (C) 2013-2019 Expedia Inc.

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
 * Some convenience methods and values for early-return behaviour.
 */
public final class EarlyReturn {
    public static final int REQUEST_BYTE_LIMIT_ON_ERROR = 1_000_000;

    public static Eventual<LiveHttpResponse> returnEarlyWithError(LiveHttpRequest request, Throwable error) {
        return request.aggregate(REQUEST_BYTE_LIMIT_ON_ERROR).flatMap(anyRequest ->
                Eventual.error(error));
    }

    public static Eventual<LiveHttpResponse> returnEarlyWithResponse(LiveHttpRequest request, LiveHttpResponse response) {
        return request.aggregate(REQUEST_BYTE_LIMIT_ON_ERROR).map(anyRequest -> response);
    }

    public static Eventual<LiveHttpResponse> returnEarlyWithResponse(LiveHttpRequest request, HttpResponse response) {
        return request.aggregate(REQUEST_BYTE_LIMIT_ON_ERROR).map(anyRequest -> response.stream());
    }

    private EarlyReturn() {
    }
}
