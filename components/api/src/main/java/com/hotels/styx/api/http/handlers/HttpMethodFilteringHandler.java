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
import io.netty.handler.codec.http.HttpMethod;
import rx.Observable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static java.lang.String.format;

/**
 * A handler that checks whether incoming messages have the expected HTTP method. If the method is correct, this handler
 * delegates to its child handler. Otherwise, it responds with a 405 error.
 */
public class HttpMethodFilteringHandler implements HttpHandler {
    private final HttpMethod method;
    private final HttpHandler httpHandler;
    private final String errorBody;

    public HttpMethodFilteringHandler(HttpMethod method, HttpHandler httpHandler) {
        this.method = checkNotNull(method);
        this.httpHandler = checkNotNull(httpHandler);
        this.errorBody = format("%s. Only [%s] is allowed for this request.", METHOD_NOT_ALLOWED.reasonPhrase(), method);
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request) {
        if (!method.equals(request.method())) {
            return Observable.just(
                    response(METHOD_NOT_ALLOWED)
                            .body(errorBody)
                            .build());
        }

        return httpHandler.handle(request);
    }
}
