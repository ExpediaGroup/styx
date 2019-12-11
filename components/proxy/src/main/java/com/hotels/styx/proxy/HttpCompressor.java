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
package com.hotels.styx.proxy;

import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Arrays;
import java.util.Collection;

/**
 * Compress HTTP responses if the encoding type is compressable.
 * List of compressable encoding types:
 * "text/plain",
 * "text/html",
 * "text/xml",
 * "text/css",
 * "text/json",
 * "application/xml",
 * "application/xhtml+xml",
 * "application/rss+xml",
 * "application/javascript",
 * "application/x-javascript",
 * "application/json"
 */
public class HttpCompressor extends HttpContentCompressor {

    private static final Collection<String> ENCODING_TYPES = Arrays.asList(
            "text/plain",
            "text/html",
            "text/xml",
            "text/css",
            "text/json",
            "application/xml",
            "application/xhtml+xml",
            "application/rss+xml",
            "application/javascript",
            "application/x-javascript",
            "application/json");


    private boolean shouldCompress(String contentType) {
        return ENCODING_TYPES.contains(contentType != null ? contentType.toLowerCase() : "");
    }

    @Override
    protected Result beginEncode(HttpResponse response, String acceptEncoding) throws Exception {
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);

        if (shouldCompress(contentType)) {
            return super.beginEncode(response, acceptEncoding);
        } else {
            return null;
        }
    }
}
