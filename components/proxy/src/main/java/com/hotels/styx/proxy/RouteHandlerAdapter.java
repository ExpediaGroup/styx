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

import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.NoServiceConfiguredException;
import com.hotels.styx.server.HttpRouter;
import rx.Observable;

/**
 * A {@link HttpHandler2} implementation.
 */
public class RouteHandlerAdapter implements HttpHandler2 {
    private final HttpRouter router;

    public RouteHandlerAdapter(HttpRouter router) {
        this.router = router;
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return router.route(request)
                .map(pipeline -> pipeline.handle(request, context))
                // TODO: NoServiceConfiguredException happens *after* routing. Therefore it doesn't contain
                // any helpful information about the state of the router as to why service was not configured.
                // It might be useful to think if there is a better way of addressing this issue.
                .orElse(Observable.error(new NoServiceConfiguredException(request.path())));
    }
}
