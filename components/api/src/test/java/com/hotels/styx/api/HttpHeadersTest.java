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

import com.hotels.styx.api.HttpHeaders.Builder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.ZonedDateTime;

import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;

public class HttpHeadersTest {
    HttpHeaders headers;

    @BeforeMethod
    public void setUp() {
        headers = new HttpHeaders.Builder()
                .add("header1", "val1")
                .add("header2", asList("val2a", "val2b"))
                .build();
    }

    @Test
    public void returnsAllRegisteredHeaderNames() {
        assertThat(headers.names(), contains("header1", "header2"));
    }

    @Test
    public void emptyHeadersHasEmptyNames() {
        HttpHeaders httpHeaders = new HttpHeaders.Builder().build();
        assertThat(httpHeaders.names(), is(emptyIterable()));
    }

    @Test
    public void checksWhetherHeadersExist() {
        assertThat(headers.contains("header1"), is(true));
        assertThat(headers.contains("header2"), is(true));
        assertThat(headers.contains("nonExistent"), is(false));
    }

    @Test
    public void providesSingleHeaderValue() {
        assertThat(headers.get("header1").get(), is("val1"));
    }

    @Test
    public void providesFirstHeaderValueWhenSeveralExist() {
        assertThat(headers.get("header2").get(), is("val2a"));
    }

    @Test
    public void providesOptionalAbsentWhenNoSuchHeaderExists() {
        assertThat(headers.get("nonExistent"), isAbsent());
    }

    @Test
    public void providesIterableOfHeaderValues() {
        assertThat(headers.getAll("header1"), contains("val1"));
        assertThat(headers.getAll("header2"), contains("val2a", "val2b"));
        assertThat(headers.getAll("nonExistent"), is(emptyIterable()));
    }

    @Test
    public void convertsHeadersToString() {
        assertThat(headers.toString(), is("[header1=val1, header2=val2a, header2=val2b]"));
    }

    @Test
    public void isIterableOfHttpHeader() {
        assertThat(headers, containsInAnyOrder(
                header("header1", "val1"),
                header("header2", "val2a"),
                header("header2", "val2b")));
    }

    @Test
    public void setsHeaders() {
        HttpHeaders newHeaders = new HttpHeaders.Builder()
                .add("foo", "bar")
                .build();

        HttpHeaders headers = new HttpHeaders.Builder(newHeaders)
                .build();

        assertThat(headers, contains(header("foo", "bar")));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void doesNotAllowNullValue() {
        new HttpHeaders.Builder()
                .add("header1", (String) null)
                .build();
    }

    @Test
    public void removesNullValues() {
        HttpHeaders headers = new Builder()
                .add("header1", asList("val1", null, "val2"))
                .build();

        assertThat(headers, contains(header("header1", "val1"), header("header1", "val2")));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void doesNotAllowNullName() {
        new HttpHeaders.Builder()
                .add(null, "value")
                .build();
    }

    @Test
    public void setsDateHeaders() {
        Instant time = ZonedDateTime.of(2015, 9, 10, 12, 2, 28, 0, UTC).toInstant();

        HttpHeaders headers = new HttpHeaders.Builder()
                .set("foo", time)
                .build();

        assertThat(headers.get("foo"), isValue("Thu, 10 Sep 2015 12:02:28 GMT"));
    }
}
