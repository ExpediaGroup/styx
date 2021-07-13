/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.server.netty.codec;

import com.hotels.styx.api.Url;
import io.netty.handler.codec.http.HttpRequest;

import java.net.URI;

import okhttp3.HttpUrl;

import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.Url.Builder.url;

final class UrlDecoder {
    private UrlDecoder() {
    }

    static Url decodeUrl(UnwiseCharsEncoder unwiseCharEncoder, HttpRequest request) {
        String host = request.headers().get(HOST);

        if (request.uri().startsWith("/") && host != null) {
            String encodedUrl = "http://" + host + unwiseCharEncoder.encode(request.uri());
            URI uri;
            try {
                uri = URI.create(encodedUrl);
            } catch (IllegalArgumentException e) {
                uri = HttpUrl.parse(encodedUrl).uri();
            }
            return new Url.Builder()
                    .path(uri.getRawPath())
                    .rawQuery(uri.getRawQuery())
                    .fragment(uri.getFragment())
                    .build();
        } else {
            return url(unwiseCharEncoder.encode(request.uri())).build();
        }
    }
}
