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
package com.hotels.styx.api.http.handlers;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import rx.Observable;

/**
 * This class provides a skeleton implementation of the {@link HttpHandler} interface, that can be used when no
 * complex {@link Observable} mechanism is required.
 */
public abstract class BaseHttpHandler implements HttpHandler {

    @Override
    public Observable<HttpResponse> handle(HttpRequest request) {
        return Observable.just(doHandle(request));
    }

    /**
     * Handles a given request and generates an appropriate response.
     *
     * @param request request
     * @return response
     */
    protected abstract HttpResponse doHandle(HttpRequest request);
}
