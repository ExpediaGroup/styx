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
package com.hotels.styx.api;

import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.domain;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

public class HttpCookieTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void acceptsOnlyNonEmptyName() {
        cookie("", "value", domain(".hotels.com"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void acceptsOnlyNonNullName() {
        cookie(null, "value", domain(".hotels.com"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptsOnlyNonNullValue() {
        cookie("name", null, domain(".hotels.com"));
    }

    @Test
    public void createsCookieWithOneAttribute() {
        HttpCookie cookie = cookie("name", "value", domain(".hotels.com"));
        assertThat(cookie.toString(), is("name=value; Domain=.hotels.com"));
        assertThat(cookie.attributes(), contains(domain(".hotels.com")));
    }

    @Test
    public void createsCookieWithMultipleAttribute() {
        HttpCookie cookie = cookie("name", "value", domain(".hotels.com"), maxAge(4000));
        assertThat(cookie.toString(), is("name=value; Domain=.hotels.com; Max-Age=4000"));
        assertThat(cookie.attributes(), containsInAnyOrder(domain(".hotels.com"), maxAge(4000)));
    }

    @Test
    public void equalsBehavesCorrectly() {
        HttpCookie base = cookie("name", "value", domain(".hotels.com"));
        HttpCookie same = cookie("name", "value", domain(".hotels.com"));
        HttpCookie different = cookie("name", "value", domain(".hotels.com"), maxAge(4000));

        assertThat(base.equals(null), is(false));
        assertThat(base.equals(base), is(true));
        assertThat(base.equals(same), is(true));
        assertThat(base.equals(different), is(false));
    }
    
    @Test(enabled = false)
    public void overwritesDuplicateAttributeTypes() {
        HttpCookie cookie = cookie("name", "value", maxAge(1234), maxAge(2345));

        assertThat(cookie.attributes(), contains(maxAge(2345)));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void attributesCannotBeNull() {
        cookie("name", "value", domain(".hotels.com"), null, maxAge(4000));
    }
}
