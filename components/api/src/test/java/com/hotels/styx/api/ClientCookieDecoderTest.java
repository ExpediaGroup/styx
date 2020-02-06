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


import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.CookieHeaderNames.SameSite.Strict;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCookieDecoderTest {

    @Test
    void decodeCookieWithSameSite() {
        String cookieValue = "hp_pos=\"\"; Domain=.dev-hotels.com; HttpOnly=true; Expires=Sun, 06 Nov 2040 08:49:37 GMT; SameSite=Strict";
        NettyCookie cookie = ClientCookieDecoder.LAX.decode(cookieValue);

        assertThat(cookie.sameSite(), equalTo(Strict));
        assertTrue(cookie.maxAge() != 0);
        assertThat(cookie.domain(), equalTo(".dev-hotels.com"));
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void decodeCookieWithInvalidSameSite() {
        String cookieValue = "hp_pos=\"\"; Domain=.dev-hotels.com; HttpOnly=true; Expires=Thu, 01-Jan-1970 00:00:10 GMT; SameSite=Anything";

        NettyCookie cookie = ClientCookieDecoder.LAX.decode(cookieValue);
        assertTrue(cookie.isHttpOnly());
        assertThat(cookie.sameSite(), is(nullValue()));

    }
}