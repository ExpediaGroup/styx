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
package com.hotels.styx.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;

import static com.google.common.collect.Maps.difference;
import static java.util.Objects.requireNonNull;
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
        mismatchDescription.appendText(String.valueOf(difference(actual, expected)));
    }
}