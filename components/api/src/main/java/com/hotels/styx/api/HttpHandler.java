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

import rx.Observable;

/**
 * Handles an {@link HttpRequest}, returning an {@link Observable} that is expected to publish a single {@link HttpResponse} value.
 */
@FunctionalInterface
public interface HttpHandler extends HttpHandler2 {
    /**
     * Processes an incoming request.
     *
     * @param request the current incoming request
     * @return an {@link Observable} that is expected to publish a single response
     */
    Observable<HttpResponse> handle(HttpRequest request);

    @Override
    default Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return handle(request);
    }

}
