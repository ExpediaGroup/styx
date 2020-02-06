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

final class CookieHeaderNames {
    public static final String PATH = "Path";

    public static final String EXPIRES = "Expires";

    public static final String MAX_AGE = "Max-Age";

    public static final String DOMAIN = "Domain";

    public static final String SECURE = "Secure";

    public static final String HTTPONLY = "HTTPOnly";

    public static final String SAMESITE = "SameSite";

    enum SameSite {
        Lax,
        Strict,
        None;

        /**
         * Return the enum value corresponding to the passed in SameSite attribute, using a case insensitive comparison.
         *
         * @param name value for the SameSite Attribute
         * @return enum value for the provided name or null
         */
        static SameSite of(String name) {

            if (name != null) {
                for (SameSite each : SameSite.class.getEnumConstants()) {
                    if (each.name().equalsIgnoreCase(name)) {
                        return each;
                    }
                }
            }
            return null;
        }
    }

    private CookieHeaderNames() {
        // Unused.
    }

}
