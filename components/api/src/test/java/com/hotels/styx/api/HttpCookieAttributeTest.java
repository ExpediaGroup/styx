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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class HttpCookieAttributeTest {
    @Test
    public void createsDomainAttribute() {
        HttpCookieAttribute attribute = HttpCookieAttribute.domain("hotels.com");

        assertThat(attribute.name(), is("Domain"));
        assertThat(attribute.value(), is("hotels.com"));
    }

    @Test
    public void createsPathAttribute() {
        HttpCookieAttribute attribute = HttpCookieAttribute.path("/");

        assertThat(attribute.name(), is("Path"));
        assertThat(attribute.value(), is("/"));
    }

    @Test
    public void createsMaxAgeAttribute() {
        HttpCookieAttribute attribute = HttpCookieAttribute.maxAge(1234);

        assertThat(attribute.name(), is("Max-Age"));
        assertThat(attribute.value(), is("1234"));
    }

    @Test
    public void createsSecureAttribute() {
        HttpCookieAttribute attribute = HttpCookieAttribute.secure();

        assertThat(attribute.name(), is("Secure"));
        assertThat(attribute.value(), is(nullValue()));
    }

    @Test
    public void createsHttpOnlyAttribute() {
        HttpCookieAttribute attribute = HttpCookieAttribute.httpOnly();

        assertThat(attribute.name(), is("HttpOnly"));
        assertThat(attribute.value(), is(nullValue()));
    }
}
