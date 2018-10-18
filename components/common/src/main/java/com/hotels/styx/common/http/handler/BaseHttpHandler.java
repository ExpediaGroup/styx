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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;

/**
 * This class provides a skeleton implementation of the {@link HttpHandler} interface, that can be used when no
 * complex {@link Eventual} mechanism is required.
 */
public abstract class BaseHttpHandler implements HttpHandler {

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return Eventual.of(doHandle(request));
    }

    /**
     * Handles a given request and generates an appropriate response.
     *
     * @param request request
     * @return response
     */
    protected abstract LiveHttpResponse doHandle(LiveHttpRequest request);
}
