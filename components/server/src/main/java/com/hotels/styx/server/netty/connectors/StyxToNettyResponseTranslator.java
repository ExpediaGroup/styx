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

import com.hotels.styx.api.HttpCookie;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.server.netty.codec.ServerCookieEncoder.LAX;
import static java.lang.Integer.parseInt;

class StyxToNettyResponseTranslator implements ResponseTranslator {

    public HttpResponse toNettyResponse(com.hotels.styx.api.HttpResponse httpResponse) {
        DefaultHttpResponse nettyResponse = new DefaultHttpResponse(httpResponse.version(), httpResponse.status(), false);

        httpResponse.headers().forEach(httpHeader ->
                nettyResponse.headers().add(httpHeader.name(), httpHeader.value()));

        httpResponse.cookies().stream()
                .map(StyxToNettyResponseTranslator::toNettyCookie)
                .map(LAX::encode)
                .forEach(setCookieHeader -> nettyResponse.headers().add(SET_COOKIE, setCookieHeader));

        return nettyResponse;
    }

    private static Cookie toNettyCookie(HttpCookie cookie) {
        Cookie nettyCookie = new DefaultCookie(cookie.name(), cookie.value());

        cookie.attributes().forEach(attribute -> {
            switch (attribute.name().toLowerCase()) {
                case "domain":
                    nettyCookie.setDomain(attribute.value());
                    break;
                case "path":
                    nettyCookie.setPath(attribute.value());
                    break;
                case "max-age":
                    nettyCookie.setMaxAge(parseInt(attribute.value()));
                    break;
                case "httponly":
                    nettyCookie.setHttpOnly(true);
                    break;
                case "secure":
                    nettyCookie.setSecure(true);
                    break;
            }
        });
        return nettyCookie;
    }

}

