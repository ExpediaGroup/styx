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
package com.hotels.styx.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.lineSeparator;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.is;

/**
 * Matcher for maps that uses guava's Maps.difference method to give a more helpful mismatch description.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapMatcher<K, V> extends TypeSafeMatcher<Map<K, V>> {
    private final Map<K, V> expected;
    private final Matcher<Map<K, V>> matcher;

    private MapMatcher(Map<K, V> expected) {
        this.expected = requireNonNull(expected);
        matcher = is(expected);
    }

    public static <K, V> MapMatcher<K, V> isMap(Map<K, V> map) {
        return new MapMatcher<>(map);
    }

    @Override
    protected boolean matchesSafely(Map<K, V> item) {
        return matcher.matches(item);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expected.toString());
    }

    @Override
    protected void describeMismatchSafely(Map<K, V> actual, Description mismatchDescription) {
        Map<K, ValueComparison<V>> diff = differences(actual, expected);

        String description = diff.entrySet().stream().map(entry ->
                "" + entry.getKey() + ":[" + entry.getValue().first + "," + entry.getValue().second + "]"
        ).collect(joining(lineSeparator()));

        mismatchDescription.appendText(description);
    }

    private Map<K, ValueComparison<V>> differences(Map<K, V> first, Map<K, V> second) {
        Map<K, ValueComparison<V>> hash = new HashMap<>();

        first.forEach((key, value) -> {
            if (value.equals(second.get(key))) {
                hash.put(key, new ValueComparison<>(value, second.get(key)));
            }
        });

        second.forEach((key, value) -> {
            if (!first.containsKey(key)) {
                hash.put(key, new ValueComparison<>(null, value));
            }
        });

        return hash;
    }

    private static class ValueComparison<V> {
        private final V first;
        private final V second;

        ValueComparison(V first, V second) {
            this.first = first;
            this.second = second;
        }
    }
}
