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

import com.hotels.styx.api.CookieHeaderNames.SameSite;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResponseCookieTest {
    @Test
    public void acceptsOnlyNonEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> responseCookie("", "value").build());
    }

    @Test
    public void acceptsOnlyNonNullName() {
        assertThrows(NullPointerException.class, () -> responseCookie(null, "value").build());
    }

    @Test
    public void acceptsOnlyNonNullValue() {
        assertThrows(NullPointerException.class, () -> responseCookie("name", null).build());
    }


    @Test
    public void acceptSameSiteCookie() {
        assertThat(
                responseCookie("name", "value").sameSite(SameSite.Lax).build().sameSite().orElse(""),
                equalTo(SameSite.Lax.name())
        );
    }

}