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

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpCookieAttribute;
import com.hotels.styx.api.Id;

import static com.hotels.styx.api.HttpCookie.cookie;
import static com.hotels.styx.api.HttpCookieAttribute.httpOnly;
import static com.hotels.styx.api.HttpCookieAttribute.maxAge;
import static com.hotels.styx.api.HttpCookieAttribute.path;

/**
 * Provides methods for handling sticky-session cookies used to identify which origin has "stuck".
 */
public final class StickySessionCookie {
    private StickySessionCookie() {
    }

    /**
     * Creates a sticky session cookie.
     *
     * @param applicationId application the origin belongs to
     * @param originId origin id
     * @param maxAge maxAge attribute for cookie
     * @return a new cookie
     */
    public static HttpCookie newStickySessionCookie(Id applicationId, Id originId, int maxAge) {
        return cookie(stickySessionCookieName(applicationId), originId.toString(), attributes(maxAge));
    }

    /**
     * Determines the name of the cookie for a particular application.
     *
     * @param applicationId application ID
     * @return the cookie name
     */
    public static String stickySessionCookieName(Id applicationId) {
        return "styx_origin_" + applicationId;
    }

    private static Iterable<HttpCookieAttribute> attributes(int maxAge) {
        return ImmutableList.of(maxAge(maxAge), path("/"), httpOnly());
    }
}
