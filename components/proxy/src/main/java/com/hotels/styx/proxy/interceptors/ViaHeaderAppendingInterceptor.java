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
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.HttpRequest;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.styx.api.HttpHeaderNames.VIA;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpHeaders.newEntity;

/**
 * Add support for "Via" header as per described in Chapter 9.9 in the HTTP/1.1 specification.
 *
 */
public class ViaHeaderAppendingInterceptor implements HttpInterceptor {
    private static final CharSequence VIA_STYX_1_0 = newEntity("1.0 styx");
    private static final CharSequence VIA_STYX_1_1 = newEntity("1.1 styx");

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        HttpRequest newRequest = request.newBuilder()
                .header(VIA, viaHeader(request))
                .build();

        return chain.proceed(newRequest)
                .map(response -> response.newBuilder()
                        .header(VIA, viaHeader(response))
                        .build());
    }

    private static CharSequence viaHeader(HttpRequest httpMessage) {
        CharSequence styxViaEntry = styxViaEntry(httpMessage.version());

        return httpMessage.headers().get(VIA)
                .map(viaHeader -> !isNullOrEmpty(viaHeader) ? viaHeader + ", " + styxViaEntry : styxViaEntry)
                .orElse(styxViaEntry);
    }

    private static CharSequence viaHeader(HttpResponse httpMessage) {
        CharSequence styxViaEntry = styxViaEntry(httpMessage.version());

        return httpMessage.headers().get(VIA)
                .map(viaHeader -> !isNullOrEmpty(viaHeader) ? viaHeader + ", " + styxViaEntry : styxViaEntry)
                .orElse(styxViaEntry);
    }

    private static CharSequence styxViaEntry(HttpVersion httpVersion) {
        return httpVersion.equals(HTTP_1_0) ? VIA_STYX_1_0 : VIA_STYX_1_1;
    }
}
