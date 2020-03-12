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

import static com.hotels.styx.api.RequestCookie.encode;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequestCookieTest {

    @Test
    public void acceptsOnlyNonEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> requestCookie("", "value"));
    }

    @Test
    public void acceptsOnlyNonNullName() {
        assertThrows(IllegalArgumentException.class, () -> requestCookie(null, "value"));
    }

    @Test
    public void acceptsOnlyNonNullValue() {
        assertThrows(NullPointerException.class, () -> requestCookie("name", null));
    }

    @Test
    public void doesNotEncodeDuplicateCookies() {
        String encoded = encode(asList(
                requestCookie("foo", "bar"),
                requestCookie("bar", "foo"),
                requestCookie("foo", "asjdfksdajf")
                )
        );

        assertThat(encoded, is("bar=foo; foo=bar"));
    }
}