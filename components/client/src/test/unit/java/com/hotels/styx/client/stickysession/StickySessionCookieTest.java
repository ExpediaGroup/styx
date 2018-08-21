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
package com.hotels.styx.client.stickysession;

import com.hotels.styx.api.ResponseCookie;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StickySessionCookieTest {
    @Test
    public void createsCookieFromApplicationOriginAndMaxAge() {
        ResponseCookie cookie = newStickySessionCookie(id("app"), id("app-01"), 86400);

        assertThat(cookie.name(), is("styx_origin_app"));
        assertThat(cookie.value(), is("app-01"));

        assertThat(cookie.maxAge(), isValue(86400L));
        assertThat(cookie.path(), isValue("/"));
        assertThat(cookie.httpOnly(), is(true));
    }

    @Test
    public void createsCookieNameFromApplicationId() {
        String name = StickySessionCookie.stickySessionCookieName(id("app"));
        assertThat(name, is("styx_origin_app"));
    }
}
