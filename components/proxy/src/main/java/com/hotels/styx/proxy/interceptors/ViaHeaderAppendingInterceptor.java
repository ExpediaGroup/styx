/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHeaderNames;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import io.netty.util.AsciiString;

import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.common.Strings.isBlank;
import static com.hotels.styx.common.Strings.isNotEmpty;

/**
 * Add support for "Via" header as per described in Chapter 9.9 in the HTTP/1.1 specification.
 *
 */
public class ViaHeaderAppendingInterceptor implements HttpInterceptor {
    private static final String VIA = "styx";
    private final CharSequence via_1_0;
    private final CharSequence via_1_1;

    public ViaHeaderAppendingInterceptor() {
        this(VIA);
    }

    public ViaHeaderAppendingInterceptor(final String via) {
        final String value = isBlank(via) ? VIA : via;
        via_1_0 = AsciiString.of("1.0 " + value);
        via_1_1 = AsciiString.of("1.1 " + value);
    }

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        LiveHttpRequest newRequest = request.newBuilder()
                .header(HttpHeaderNames.VIA, viaHeader(request))
                .build();

        return chain.proceed(newRequest)
                .map(response -> response.newBuilder()
                        .header(HttpHeaderNames.VIA, viaHeader(response))
                        .build());
    }

    private CharSequence viaHeader(LiveHttpRequest httpMessage) {
        CharSequence styxViaEntry = styxViaEntry(httpMessage.version());

        return httpMessage.headers().get(HttpHeaderNames.VIA)
                .map(viaHeader -> isNotEmpty(viaHeader) ? viaHeader + ", " + styxViaEntry : styxViaEntry)
                .orElse(styxViaEntry);
    }

    private CharSequence viaHeader(LiveHttpResponse httpMessage) {
        CharSequence styxViaEntry = styxViaEntry(httpMessage.version());

        return httpMessage.headers().get(HttpHeaderNames.VIA)
                .map(viaHeader -> isNotEmpty(viaHeader) ? viaHeader + ", " + styxViaEntry : styxViaEntry)
                .orElse(styxViaEntry);
    }

    private CharSequence styxViaEntry(HttpVersion httpVersion) {
        return httpVersion.equals(HTTP_1_0) ? via_1_0 : via_1_1;
    }
}
