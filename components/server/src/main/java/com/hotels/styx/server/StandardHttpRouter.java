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
package com.hotels.styx.server;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;

import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;

/**
 * Simple Http Router.
 */
public class StandardHttpRouter implements HttpHandler {
    private static final HttpHandler NOT_FOUND_HANDLER = (request, context) -> StyxObservable.of(HttpResponse.response(NOT_FOUND).build());

    private final PathTrie<HttpHandler> routes = new PathTrie<>();

    @Override
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return routes.get(request.path())
                .orElse(NOT_FOUND_HANDLER)
                .handle(request, context);
    }

    public StandardHttpRouter add(String path, HttpHandler httpHandler) {
        routes.put(path, httpHandler);
        return this;
    }

}
