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
package com.hotels.styx.proxy;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.HttpRouter;
import com.hotels.styx.server.NoServiceConfiguredException;

/**
 * A {@link HttpHandler} implementation.
 */
public class RouteHandlerAdapter implements HttpHandler {
    private final HttpRouter router;

    public RouteHandlerAdapter(HttpRouter router) {
        this.router = router;
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return router.route(request, context)
                .map(pipeline -> pipeline.handle(request, context))
                .orElseGet(() -> Eventual.error(new NoServiceConfiguredException(request.path())));
    }
}
