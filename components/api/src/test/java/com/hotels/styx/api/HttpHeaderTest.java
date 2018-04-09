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
package com.hotels.styx.api;

import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpHeader.header;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class HttpHeaderTest {
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsEmptyValuesList() {
        header("name", new String[0]);
    }

    @Test
    public void createsSingleValueHeader() {
        HttpHeader header = header("name", "value");
        assertThat(header.toString(), is("name=value"));
        assertThat(header.value(), is("value"));
        assertThat(header.values(), contains("value"));
    }

    @Test
    public void createsMultipleValueHeader() {
        HttpHeader header = header("name", "value1", "value2");
        assertThat(header.toString(), is("name=value1, value2"));
        assertThat(header.value(), is("value1"));
        assertThat(header.values(), contains("value1", "value2"));
    }

    @Test
    public void equalsBehavesCorrectly() {
        HttpHeader base = header("name", "value");
        HttpHeader same = header("name", "value");
        HttpHeader different = header("name", "value1");

        assertThat(base.equals(null), is(false));
        assertThat(base.equals(base), is(true));
        assertThat(base.equals(same), is(true));
        assertThat(base.equals(different), is(false));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void valuesCannotBeNull() {
        header("name", "value1", null, "value2");
    }
}
