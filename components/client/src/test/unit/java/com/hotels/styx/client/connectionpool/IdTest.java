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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.Id;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.id;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class IdTest {

    @Test
    public void checkEquality() {
        Id base = id("one");
        Id same = id("one");
        Id different = id("two");

        assertThat(base.equals(null), is(false));
        assertThat(base.equals(base), is(true));
        assertThat(base.equals(same), is(true));
        assertThat(base.equals(different), is(false));
    }
}