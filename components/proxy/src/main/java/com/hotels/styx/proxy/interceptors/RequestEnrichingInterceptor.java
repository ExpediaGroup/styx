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
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.client.StyxHeaderConfig;

import static com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_FOR;
import static com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_PROTO;
import com.hotels.styx.api.HttpRequest;


/**
 * Adds X-Forwarded-For, X-Forwarded-Proto and X-Hcom-Request-Id headers to requests.
 */
public class RequestEnrichingInterceptor implements HttpInterceptor {
    private final CharSequence requestIdHeaderName;

    public RequestEnrichingInterceptor(StyxHeaderConfig styxHeaderConfig) {
        this.requestIdHeaderName = styxHeaderConfig.requestIdHeaderName();
    }

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(enrich(request));
    }

    private HttpRequest enrich(HttpRequest request) {
        return request.newBuilder()
                .header(requestIdHeaderName, request.id())
                .header(X_FORWARDED_FOR, xForwardedFor(request))
                .header(X_FORWARDED_PROTO, xForwardedProto(request))
                .build();
    }

    private static CharSequence xForwardedProto(HttpRequest request) {
        return request
                .header(X_FORWARDED_PROTO)
                .orElse(request.isSecure() ? "https" : "http");
    }

    private static String xForwardedFor(HttpRequest request) {
        String hostName = request.clientAddress().getHostString();

        return request.header(X_FORWARDED_FOR)
                .map(xForwardedFor -> xForwardedFor + ", " + hostName)
                .orElse(hostName);
    }
}
