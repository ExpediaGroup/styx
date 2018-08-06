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
package com.hotels.styx.common;

import com.google.common.base.Joiner;

/**
 * Common uses of Guava's Joiner class.
 */
public final class Joiners {
    public static final Joiner JOINER_ON_COMMA = Joiner.on(",").skipNulls();
    public static final Joiner JOINER_ON_SEMI_COLON = Joiner.on(";").skipNulls();
    public static final Joiner JOINER_ON_SPACE = Joiner.on(" ").skipNulls();
    public static final Joiner JOINER_ON_NEW_LINE = Joiner.on("\n").skipNulls();

    private Joiners() {
    }
}
