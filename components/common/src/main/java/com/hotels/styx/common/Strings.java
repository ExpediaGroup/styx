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
package com.hotels.styx.common;

public final class Strings {

    public static final boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static final boolean isNotEmpty(String s) {
        return !isNullOrEmpty(s);
    }

    public static final boolean isBlank(final String s) {
        return isNullOrEmpty(s) || isNullOrEmpty(s.trim());
    }

    public static final boolean isNotBlank(final String s) {
        return !isBlank(s);
    }

    private Strings() {
        // Not used.
    }
}
