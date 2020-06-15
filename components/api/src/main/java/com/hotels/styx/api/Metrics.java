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

import java.util.StringJoiner;

public final class Metrics {

    public static final String APPID_TAG = "appid";
    public static final String ORIGINID_TAG = "originid";

    private Metrics() {
    }

    public static String name(String... parts) {
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            if (part != null && part.length() > 0) {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }
}
