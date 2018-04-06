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

import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.Matchers.equalTo;

/**
 *
 */
public class ContainsExactlyOneMatcher<T> extends TypeSafeMatcher<Iterable<T>> {
    private final Matcher<T> expected;

    private ContainsExactlyOneMatcher(Matcher<T> expected) {
        this.expected = expected;
    }

    public static <T> Matcher<Iterable<T>> containsExactlyOne(T item) {
        return containsExactlyOne(equalTo(item));
    }

    public static <T> Matcher<Iterable<T>> containsExactlyOne(Matcher<T> itemMatcher) {
        return new ContainsExactlyOneMatcher<>(itemMatcher);
    }

    @Override
    protected boolean matchesSafely(Iterable<T> elements) {
        return numberOfMatchingElements(elements) == 1;
    }

    private long numberOfMatchingElements(Iterable<T> iterable) {
        return stream(iterable.spliterator(), false)
                .filter(expected::matches)
                .count();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Iterable with exactly one element matching: ");
        expected.describeTo(description);
    }

    @Override
    protected void describeMismatchSafely(Iterable<T> iterable, Description mismatchDescription) {
        long count = numberOfMatchingElements(iterable);

        mismatchDescription.appendText("Iterable with " + count + " matching elements: " + iterable);
    }
}
