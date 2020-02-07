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

import io.netty.handler.codec.http.cookie.DefaultCookie;

import static com.hotels.styx.api.CookieUtil.stringBuilder;


class NettyCookie extends DefaultCookie {


    private String sameSite;
    /**
     * Creates a new cookie with the specified name and value.
     *
     * @param name
     * @param value
     */
    public NettyCookie(String name, String value) {
        super(name, value);
    }


    public String sameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    @Override
    public String toString() {
        StringBuilder buf = stringBuilder()
                .append(name())
                .append('=')
                .append(value());
        if (domain() != null) {
            buf.append(", domain=")
                    .append(domain());
        }
        if (path() != null) {
            buf.append(", path=")
                    .append(path());
        }
        if (maxAge() != 0) {
            buf.append(", maxAge=")
                    .append(maxAge())
                    .append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
        }
        if (sameSite != null) {
            buf.append(", SameSite=").append(sameSite.toString());
        }
        return buf.toString();
    }
}
