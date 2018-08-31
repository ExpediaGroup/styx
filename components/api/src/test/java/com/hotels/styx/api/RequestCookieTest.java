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

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static com.hotels.styx.api.RequestCookie.encode;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RequestCookieTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void acceptsOnlyNonEmptyName() {
        requestCookie("", "value");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void acceptsOnlyNonNullName() {
        requestCookie(null, "value");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void acceptsOnlyNonNullValue() {
        requestCookie("name", null);
    }

    @Test
    public void doesNotEncodeDuplicateCookies() {
        String encoded = encode(ImmutableList.of(
                requestCookie("foo", "bar"),
                requestCookie("bar", "foo"),
                requestCookie("foo", "asjdfksdajf")
                )
        );

        assertThat(encoded, is("bar=foo; foo=bar"));
    }
}