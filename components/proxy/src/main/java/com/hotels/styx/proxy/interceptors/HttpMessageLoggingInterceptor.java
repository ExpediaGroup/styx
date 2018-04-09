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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.logging.HttpRequestMessageLogger;
import rx.Observable;

/**
 * Logs requests and responses when enabled. Disabled by default.
 */
public class HttpMessageLoggingInterceptor implements HttpInterceptor {

    private final HttpRequestMessageLogger logger;

    public HttpMessageLoggingInterceptor(boolean longFormatEnabled) {
        this.logger = new HttpRequestMessageLogger("com.hotels.styx.http-messages.inbound", longFormatEnabled);
    }

    @Override
    public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        log(request);
        return chain.proceed(request).doOnNext(this::log);
    }

    private void log(HttpResponse response) {
        logger.logResponse(response.request(), response);
    }

    private void log(HttpRequest request) {
        // Note that the request ID is repeated for request logging so that a single search term can be used to find both request and response logs.
        logger.logRequest(request, null);
    }
}
