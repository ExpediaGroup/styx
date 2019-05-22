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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;

import static java.util.Objects.requireNonNull;

/**
 * Adapts Streaming HttpHandler API to STATIC WebServiceHandler interface.
 */
public class HttpStreamer implements WebServiceHandler {
    private static final int KILOBYTE = 1024;

    private int bytes;
    private HttpHandler delegate;

    /**
     * HttpStreamer constructor.
     *
     * @param bytes max number of content bytes to aggregate
     * @param delegate adapted HttpHandler instance
     */
    public HttpStreamer(int bytes, HttpHandler delegate) {
        this.bytes = bytes;
        this.delegate = requireNonNull(delegate);
    }

    /**
     * HttpStreamer constructor.
     *
     * @param delegate adapted HttpHandler instance
     */
    public HttpStreamer(HttpHandler delegate) {
        this(125 * KILOBYTE, delegate);
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return delegate.handle(request.stream(), context)
                .flatMap(live -> live.aggregate(bytes));
    }
}
