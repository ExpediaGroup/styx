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
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;

import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.KEEP_ALIVE;
import static com.hotels.styx.api.HttpHeaderNames.PROXY_AUTHENTICATE;
import static com.hotels.styx.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static com.hotels.styx.api.HttpHeaderNames.TE;
import static com.hotels.styx.api.HttpHeaderNames.TRAILER;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderNames.UPGRADE;
import com.hotels.styx.api.LiveHttpRequest;

/**
 * Removes Hop-By-Hop headers.
 */
public class HopByHopHeadersRemovingInterceptor implements HttpInterceptor {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(removeHopByHopHeaders(request))
                .map(HopByHopHeadersRemovingInterceptor::removeHopByHopHeaders);
    }

    private static LiveHttpResponse removeHopByHopHeaders(LiveHttpResponse response) {
        LiveHttpResponse.Transformer newResponse = response.newBuilder();

        response.header(CONNECTION).ifPresent(connection -> {
            for (String connectToken : connection.split(",")) {
                String header = connectToken.trim();
                newResponse.removeHeader(header);
            }
            newResponse.removeHeader(CONNECTION);
        });

        newResponse
                .removeHeader(KEEP_ALIVE)
                .removeHeader(PROXY_AUTHENTICATE)
                .removeHeader(PROXY_AUTHORIZATION)
                .removeHeader(TE)
                .removeHeader(TRAILER)
                .removeHeader(TRANSFER_ENCODING)
                .removeHeader(UPGRADE);

        return newResponse.build();
    }

    private static LiveHttpRequest removeHopByHopHeaders(LiveHttpRequest request) {
        LiveHttpRequest.Transformer newRequest = request.newBuilder();

        request.header(CONNECTION).ifPresent(connection -> {
            for (String connectToken : connection.split(",")) {
                String header = connectToken.trim();
                newRequest.removeHeader(header);
            }
            newRequest.removeHeader(CONNECTION);
        });

        newRequest
                .removeHeader(KEEP_ALIVE)
                .removeHeader(PROXY_AUTHENTICATE)
                .removeHeader(PROXY_AUTHORIZATION)
                .removeHeader(TE)
                .removeHeader(TRAILER)
                .removeHeader(UPGRADE);

        return newRequest.build();
    }
}
