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

import org.testng.annotations.Test;

import static com.hotels.styx.common.MorePreconditions.checkArgument;
import static com.hotels.styx.common.MorePreconditions.inRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.testng.Assert.fail;

public class MorePreconditionsTest {

    @Test
    public void checkArgumentSimpleMessageFailure() {
        try {
            checkArgument(0, greaterThan(0));
            fail("no exception thrown");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), containsString("argument\n" +
                    "Expected: a value greater than <0>\n" +
                    "     but: <0> was equal to <0>"));
        }
    }

    @Test
    public void checkArgumentFullMessageFailure() {
        try {
            checkArgument(0, greaterThan(0), "healthy count threshold");
            fail("no exception thrown");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), containsString("healthy count threshold\n" +
                    "Expected: a value greater than <0>\n" +
                    "     but: <0> was equal to <0>"));
        }
    }

    @Test
    public void checkArgumentInRange() {
        assertThat(checkArgument(0, inRange(-3, 3)), is(0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void checkArgumentInRangeFailure() {
        checkArgument(0, inRange(3, 4));
    }

}


