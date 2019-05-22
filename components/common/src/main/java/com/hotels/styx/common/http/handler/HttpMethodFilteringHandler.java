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
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;

import java.nio.charset.StandardCharsets;

import static com.hotels.styx.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A handler that checks whether incoming messages have the expected HTTP method. If the method is correct, this handler
 * delegates to its child handler. Otherwise, it responds with a 405 error.
 */
public class HttpMethodFilteringHandler implements WebServiceHandler {
    private final HttpMethod method;
    private final WebServiceHandler httpHandler;
    private final String errorBody;

    public HttpMethodFilteringHandler(HttpMethod method, WebServiceHandler httpHandler) {
        this.method = requireNonNull(method);
        this.httpHandler = requireNonNull(httpHandler);
        this.errorBody = format("%s. Only [%s] is allowed for this request.", METHOD_NOT_ALLOWED.description(), method);
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        if (!method.equals(request.method())) {
            return Eventual.of(
                    HttpResponse.response(METHOD_NOT_ALLOWED)
                            .body(errorBody, StandardCharsets.UTF_8)
                            .build()
            );
        }

        return httpHandler.handle(request, context);
    }
}
