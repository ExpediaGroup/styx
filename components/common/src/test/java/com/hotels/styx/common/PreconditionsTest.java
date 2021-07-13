/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import org.junit.jupiter.api.Test;

import static com.hotels.styx.common.Preconditions.checkArgument;
import static com.hotels.styx.common.Preconditions.checkNotEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PreconditionsTest {

    @Test
    public void isNotEmptyString() {
        assertThat(checkNotEmpty(" "), is(" ") );
    }

    @Test
    public void isEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> checkNotEmpty(null));
    }

    @Test
    public void checkArgumentFailure() {
        assertThrows(IllegalArgumentException.class,
                () -> checkArgument(0, false));
    }

    @Test
    public void checkArgumentSuccess() {
        assertThat(checkArgument(0, true), is(0));
    }

}
