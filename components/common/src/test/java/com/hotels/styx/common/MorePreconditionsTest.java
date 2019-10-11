/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

public class MorePreconditionsTest {

    @Test
    public void checkArgumentSimpleMessageFailure() {
        try {
            checkArgument(0, false, "Value should be greater than zero");
            fail("no exception thrown");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), is("Value should be greater than zero"));
        }
    }

    @Test
    public void checkArgumentFullMessageFailure() {
        try {
            checkArgument(0, false, "Healthy count threshold");
            fail("no exception thrown");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), is("Healthy count threshold"));
        }
    }

    @Test
    public void returnsTheValueUnchanged() {
        assertThat(checkArgument(0, true, ""), is(0));
    }

}


