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
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.client.StyxHeaderConfig;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_FOR;
import static com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_PROTO;
import static org.slf4j.LoggerFactory.getLogger;


/**
 * Adds X-Forwarded-For, X-Forwarded-Proto and X-Hcom-Request-Id headers to requests.
 */
public class RequestEnrichingInterceptor implements HttpInterceptor {
    private static final Logger LOGGER = getLogger(RequestEnrichingInterceptor.class);

    private final CharSequence requestIdHeaderName;

    public RequestEnrichingInterceptor(StyxHeaderConfig styxHeaderConfig) {
        this.requestIdHeaderName = styxHeaderConfig.requestIdHeaderName();
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(enrich(request, chain.context()));
    }

    private LiveHttpRequest enrich(LiveHttpRequest request, Context context) {
        LiveHttpRequest.Transformer builder = request.newBuilder();

        xForwardedFor(request, context)
                .ifPresent(headerValue -> builder.header(X_FORWARDED_FOR, headerValue));

        return builder
                .header(requestIdHeaderName, request.id())
                .header(X_FORWARDED_PROTO, xForwardedProto(request, context.isSecure()))
                .build();
    }

    private static Optional<String> xForwardedFor(LiveHttpRequest request, HttpInterceptor.Context context) {
        Optional<String> maybeClientAddress = context.clientAddress()
                .map(InetSocketAddress::getHostString)
                .map(hostName -> request
                        .header(X_FORWARDED_FOR)
                        .map(xForwardedFor -> xForwardedFor + ", " + hostName)
                        .orElse(hostName));

        if (!maybeClientAddress.isPresent()) {
            LOGGER.warn("No clientAddress in context url={}", request.url());
        }

        return maybeClientAddress;
    }

    private static CharSequence xForwardedProto(LiveHttpRequest request, boolean secure) {
        return request
                .header(X_FORWARDED_PROTO)
                .orElse(secure ? "https" : "http");
    }
}
