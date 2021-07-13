/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.WebServiceHandler;

import static java.util.Objects.requireNonNull;

/**
 * Adapts a static WebServiceHandler to streaming HttpHandler interface.
 */
public class HttpAggregator implements HttpHandler {

    private static final int KILOBYTE = 1024;

    private final WebServiceHandler delegate;
    private final int bytes;

    /**
     * HttpAggregator Constructor.
     *
     * @param bytes max number of bytes to aggregate
     * @param delegate adapted WebServiceHandler endpoint
     */
    public HttpAggregator(int bytes, WebServiceHandler delegate) {
        this.delegate = requireNonNull(delegate);
        this.bytes = bytes;
    }

    /**
     * HttpAggregator constructor.
     *
     * @param delegate adapted WebServiceHandler endpoint
     */
    public HttpAggregator(WebServiceHandler delegate) {
        this(120 * KILOBYTE, delegate);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return request.aggregate(bytes)
                .flatMap(aggregated -> this.delegate.handle(aggregated, context))
                .map(HttpResponse::stream);
    }
}
