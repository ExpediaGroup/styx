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
package com.hotels.styx.api.cookies;

import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.cookies.ResponseCookie.cookie;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

public class ResponseCookieTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void acceptsOnlyNonEmptyName() {
        cookie("", "value", domain(".hotels.com"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptsOnlyNonNullName() {
        cookie(null, "value", domain(".hotels.com"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptsOnlyNonNullValue() {
        cookie("name", null, domain(".hotels.com"));
    }

    @Test
    public void createsCookieWithOneAttribute() {
        ResponseCookie cookie = cookie("name", "value", domain(".hotels.com"));
        assertThat(cookie.toString(), is("name=value; Domain=.hotels.com"));
        assertThat(cookie.attributes(), contains(domain(".hotels.com")));
    }

    @Test
    public void createsCookieWithMultipleAttribute() {
        ResponseCookie cookie = cookie("name", "value", domain(".hotels.com"), maxAge(4000));
        assertThat(cookie.toString(), is("name=value; Domain=.hotels.com; Max-Age=4000"));
        assertThat(cookie.attributes(), containsInAnyOrder(domain(".hotels.com"), maxAge(4000)));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void attributesCannotBeNull() {
        cookie("name", "value", domain(".hotels.com"), null, maxAge(4000));
    }
}