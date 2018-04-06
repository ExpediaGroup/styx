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

import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.support.PathTrie;
import rx.Observable;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static rx.Observable.just;

/**
 * Simple Http Router.
 */
public class StandardHttpRouter implements HttpHandler2 {
    private static final HttpHandler2 NOT_FOUND_HANDLER = (request, context) -> just(response(NOT_FOUND).build());

    private final PathTrie<HttpHandler2> routes = new PathTrie<>();

    @Override
    public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return routes.get(request.path())
                .orElse(NOT_FOUND_HANDLER)
                .handle(request, context);
    }

    public StandardHttpRouter add(String path, HttpHandler2 httpHandler) {
        routes.put(path, httpHandler);
        return this;
    }

}
