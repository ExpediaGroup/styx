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

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.ResponseCookie;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpHeaderNames.SET_COOKIE;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertTrue;

public class StyxToNettyResponseTranslatorTest {
    private StyxToNettyResponseTranslator translator = new StyxToNettyResponseTranslator();

    @Test
    public void shouldCreateNettyResponseWithoutHeaders() throws Exception {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .version(HTTP_1_1)
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertThat(nettyResponse.status(), equalTo(io.netty.handler.codec.http.HttpResponseStatus.OK));
        assertThat(nettyResponse.protocolVersion(), equalTo(io.netty.handler.codec.http.HttpVersion.HTTP_1_1));
    }

    @Test
    public void shouldCreateNettyResponseWithHostHeader() {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .header("Host", "localhost")
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertTrue(nettyResponse.headers().containsValue("Host", "localhost", false));
    }

    @Test
    public void shouldCreateNettyResponseWithCookieWithAttributes() {
        ResponseCookie cookie = responseCookie("cookie-test", "cookie-value")
                .domain("cookie-domain")
                .path("cookie-path")
                .maxAge(1234)
                .httpOnly(true)
                .secure(true)
                .build();

        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .cookies(cookie)
                .build();

        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);

        String setCookie = nettyResponse.headers().get(SET_COOKIE);

        Cookie nettyCookie = ClientCookieDecoder.LAX.decode(setCookie);

        assertThat(nettyCookie.name(), is("cookie-test"));
        assertThat(nettyCookie.value(), is("cookie-value"));
        assertThat(nettyCookie.domain(), is("cookie-domain"));
        assertThat(nettyCookie.maxAge(), is(1234L));
        assertThat(nettyCookie.isHttpOnly(), is(true));
        assertThat(nettyCookie.isSecure(), is(true));
    }
}