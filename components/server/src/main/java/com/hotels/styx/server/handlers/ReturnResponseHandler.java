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
package com.hotels.styx.server.handlers;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;

import java.util.function.Supplier;

import static com.hotels.styx.api.FullHttpResponse.response;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A handler that return whatever response returned from the passed in handler.
 *
 */
public final class ReturnResponseHandler extends BaseHttpHandler {
    private final Supplier<HttpResponse> response;

    private ReturnResponseHandler(Supplier<HttpResponse> response) {
        this.response = response;
    }

    protected HttpResponse doHandle(HttpRequest request) {
        return response.get();
    }

    public static HttpHandler returnsResponse(String response) {
        return returnsResponse(() -> response().body(response, UTF_8).build().toStreamingResponse());
    }

    public static HttpHandler returnsResponse(Supplier<HttpResponse> responseSupplier) {
        return new ReturnResponseHandler(responseSupplier);
    }
}
