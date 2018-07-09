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

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

public class MapStreamTest {
    private final Map<String, String> map = ImmutableMap.of(
            "firstName", "John",
            "lastName", "Smith",
            "dateOfBirth", "1970-01-01"
    );

    @Test
    public void filters() {
        Map<String, String> result = MapStream.stream(map)
                .filter((key, value) -> key.endsWith("Name"))
                .toMap();

        assertThat(result.size(), is(2));
        assertThat(result, hasEntry("firstName", "John"));
        assertThat(result, hasEntry("lastName", "Smith"));
    }

    @Test
    public void mapsKeys() {
        Map<String, String> result = MapStream.stream(map)
                .mapKey((key, value) -> "foo." + key)
                .toMap();

        assertThat(result.size(), is(3));
        assertThat(result, hasEntry("foo.firstName", "John"));
        assertThat(result, hasEntry("foo.lastName", "Smith"));
        assertThat(result, hasEntry("foo.dateOfBirth", "1970-01-01"));
    }

    @Test
    public void mapsValues() {
        Map<String, String> result = MapStream.stream(map)
                .mapValue((key, value) -> value.toUpperCase())
                .toMap();

        assertThat(result.size(), is(3));
        assertThat(result, hasEntry("firstName", "JOHN"));
        assertThat(result, hasEntry("lastName", "SMITH"));
        assertThat(result, hasEntry("dateOfBirth", "1970-01-01"));
    }

    @Test
    public void mapsEntries() {
        Map<String, String> result = MapStream.stream(map)
                .map((key, value) -> new SimpleEntry<>(
                        "foo." + key,
                        value.toUpperCase()
                ))
                .toMap();

        assertThat(result.size(), is(3));
        assertThat(result, hasEntry("foo.firstName", "JOHN"));
        assertThat(result, hasEntry("foo.lastName", "SMITH"));
        assertThat(result, hasEntry("foo.dateOfBirth", "1970-01-01"));
    }

    @Test
    public void mapsToObject() {
        Set<String> result = MapStream.stream(map)
                .mapToObject((key, value) -> key + "=" + value)
                .collect(toSet());

        assertThat(result, containsInAnyOrder("firstName=John", "lastName=Smith", "dateOfBirth=1970-01-01"));
    }
}