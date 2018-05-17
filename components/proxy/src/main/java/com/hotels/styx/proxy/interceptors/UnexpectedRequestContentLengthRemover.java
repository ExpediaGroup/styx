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

import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import com.hotels.styx.api.HttpRequest;

/**
 * Fixes bad content length headers.
 */
public class UnexpectedRequestContentLengthRemover implements HttpInterceptor {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(removeBadContentLength(request));
    }

    private static HttpRequest removeBadContentLength(HttpRequest request) {
        Optional<Integer> contentLength = request.contentLength();
        if (contentLength.isPresent() && request.chunked()) {
            return request.newBuilder()
                    .removeHeader(CONTENT_LENGTH)
                    .build();
        }
        return request;
    }
}
