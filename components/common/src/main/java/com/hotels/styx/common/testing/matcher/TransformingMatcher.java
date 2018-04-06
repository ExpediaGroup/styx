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
package com.hotels.styx.common.testing.matcher;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A matcher that transforms a value and then applies another matcher to it when used.
 * <p>
 * The advantage of this over transforming the value inside the test code is that it can be re-used while maintaining
 * the familiar syntax of hamcrest assertThat.
 *
 * @param <E> type to match against
 * @param <R> post-transformation type of delegate matcher
 */
public class TransformingMatcher<E, R> extends TypeSafeMatcher<E> {
    private final Function<E, R> transformation;
    private final Matcher<R> matcher;

    protected TransformingMatcher(Function<E, R> transformation, Matcher<R> matcher) {
        this.transformation = requireNonNull(transformation);
        this.matcher = requireNonNull(matcher);
    }

    public static <E, R> TransformingMatcher<E, R> hasDerivedValue(Function<E, R> transformation, Matcher<R> matcher) {
        return new TransformingMatcher<>(transformation, matcher);
    }

    @Override
    protected boolean matchesSafely(E e) {
        return matcher.matches(transformation.apply(e));
    }

    @Override
    public void describeTo(Description description) {
        matcher.describeTo(description);
    }

    @Override
    protected void describeMismatchSafely(E item, Description mismatchDescription) {
        matcher.describeMismatch(transformation.apply(item), mismatchDescription);
    }
}
