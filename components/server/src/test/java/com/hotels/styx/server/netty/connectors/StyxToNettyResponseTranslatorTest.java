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

import com.hotels.styx.api.HttpCookieAttribute;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.httpOnly;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;
import static com.hotels.styx.api.HttpCookieAttribute.secure;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.assertTrue;

public class StyxToNettyResponseTranslatorTest {
    private StyxToNettyResponseTranslator translator = new StyxToNettyResponseTranslator();

    @Test
    public void shouldCreateNettyResponseWithoutHeaders() throws Exception {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .version(HTTP_1_1)
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertThat(nettyResponse.status(), equalTo(OK));
        assertThat(nettyResponse.protocolVersion(), equalTo(HTTP_1_1));
    }

    @Test
    public void shouldCreateNettyResponseWithHostHeader() {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .header("Host", "localhost")
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertTrue(nettyResponse.headers().containsValue("Host", "localhost", false));
    }

    @Test(dataProvider = "attributes")
    public void shouldCreateNettyResponseWithCookieWithAttributes(HttpCookieAttribute attribute, String attributeString) {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .addCookie(cookie("cookie-test", "cookie-value", singleton(attribute)))
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertTrue(nettyResponse.headers().containsValue("Set-Cookie", "cookie-test=cookie-value; " + attributeString,
                false));
    }

    @DataProvider
    public static Object[][] attributes() {
        return new Object[][]{
                { domain("cookie-domain"), domain("cookie-domain").toString()},
                { path("cookie-path"), path("cookie-path").toString()},
                { secure(), secure().toString() },
                { httpOnly(), "HTTPOnly" }
        };
    }

    @Test()
    public void shouldCreateNettyResponseWithCookieWithMaxAge() {
        HttpResponse styxResponse = new HttpResponse.Builder(OK)
                .addCookie(cookie("cookie-test", "cookie-value", singleton(maxAge(1))))
                .build();
        io.netty.handler.codec.http.HttpResponse nettyResponse = translator.toNettyResponse(styxResponse);
        assertTrue(nettyResponse.headers().get("Set-Cookie").startsWith("cookie-test=cookie-value; Max-Age=1; Expires="));
    }
}