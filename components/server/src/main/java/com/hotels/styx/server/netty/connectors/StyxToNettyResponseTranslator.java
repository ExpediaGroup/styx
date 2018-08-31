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
package com.hotels.styx.server.netty.connectors;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

class StyxToNettyResponseTranslator implements ResponseTranslator {

    public HttpResponse toNettyResponse(com.hotels.styx.api.HttpResponse httpResponse) {
        io.netty.handler.codec.http.HttpVersion version = toNettyVersion(httpResponse.version());
        HttpResponseStatus httpResponseStatus = HttpResponseStatus.valueOf(httpResponse.status().code());

        DefaultHttpResponse nettyResponse = new DefaultHttpResponse(version, httpResponseStatus, false);

        httpResponse.headers().forEach(httpHeader ->
                nettyResponse.headers().add(httpHeader.name(), httpHeader.value()));

        return nettyResponse;
    }

    private static HttpVersion toNettyVersion(com.hotels.styx.api.HttpVersion version) {
        return HttpVersion.valueOf(version.toString());
    }
}

