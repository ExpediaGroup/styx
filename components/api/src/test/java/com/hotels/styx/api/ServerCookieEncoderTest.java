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
package com.hotels.styx.api;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

public class ServerCookieEncoderTest {

    @Test
    public void removesMaxAgeInFavourOfExpireIfDateIsInThePast() {
        String cookieValue = "hp_pos=\"\"; Domain=.dev-hotels.com; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Path=/";
        Cookie cookie = ClientCookieDecoder.LAX.decode(cookieValue);

        String encodedCookieValue = ServerCookieEncoder.LAX.encode(cookie);
        assertThat(encodedCookieValue, not(containsString("Max-Age=0")));
        assertThat(encodedCookieValue, containsString("Expires=Thu, 01 Jan 1970 00:00"));
    }

    @Test
    public void willNotModifyMaxAgeIfPositive() {
        String cookieValue = "hp_pos=\"\"; Domain=.dev-hotels.com; Max-Age=50; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Path=/";
        Cookie cookie = ClientCookieDecoder.LAX.decode(cookieValue);
        assertThat(cookie.maxAge(), is(50L));
        assertThat(ServerCookieEncoder.LAX.encode(cookie), containsString("Max-Age=50"));
    }

    @Test
    public void setValidSameSite() {
        DefaultCookie cookie = new DefaultCookie("key", "value");
        cookie.setSameSite(SameSite.Lax);
        cookie.setDomain(".domain.com");
        cookie.setMaxAge(50);

        assertThat(ServerCookieEncoder.LAX.encode(cookie), containsString("Lax"));
    }

//    TODO why was this here?
//    @Test
//    public void setAnySameSite() {
//        NettyCookie cookie = new NettyCookie("key", "value");
//        cookie.setSameSite("Test");
//        assertThat(ServerCookieEncoder.LAX.encode(cookie), containsString("SameSite=Test"));
//    }
}