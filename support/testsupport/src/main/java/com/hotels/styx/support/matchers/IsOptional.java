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

import com.google.common.base.Objects;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Optional;

/**
 * Provides matchers around the {@code Optional} class
 *
 * @param <T>
 * @author john.butler
 * @see Optional
 */
public final class IsOptional<T> extends TypeSafeMatcher<Optional<? extends T>> {

    /**
     * Checks that the passed Optional is not present
     */
    public static IsOptional<Object> isAbsent() {
        return new IsOptional<>(false);
    }

    /**
     * Checks that the passed Optional is present
     */
    public static IsOptional<Object> isPresent() {
        return new IsOptional<>(true);
    }

    /**
     * Checks that the passed Option is Some and that the contains value matches
     * {@code value} based on {@code Objects.equal}
     *
     * @see Objects#equal(Object, Object)
     */
    public static <T> IsOptional<T> isValue(T value) {
        return new IsOptional<>(value);
    }

    public static <T> IsOptional<T> matches(Matcher<T> matcher) {
        return new IsOptional<>(matcher);
    }

    public static <T extends Iterable> IsOptional<T> isIterable(Matcher<? extends Iterable> matcher) {
        return new IsOptional<>((Matcher) matcher);
    }

    private final boolean someExpected;
    private final Optional<T> expected;
    private final Optional<Matcher<T>> matcher;

    private IsOptional(boolean someExpected) {
        this.someExpected = someExpected;
        this.expected = Optional.empty();
        this.matcher = Optional.empty();
    }

    private IsOptional(T value) {
        this.someExpected = true;
        this.expected = Optional.of(value);
        this.matcher = Optional.empty();
    }

    private IsOptional(Matcher<T> matcher) {
        this.someExpected = true;
        this.expected = Optional.empty();
        this.matcher = Optional.of(matcher);
    }

    @Override
    public void describeTo(Description description) {
        if (!someExpected) {
            description.appendText("<Absent>");
        } else if (expected.isPresent()) {
            description.appendValue(expected);
        } else if (matcher.isPresent()) {
            description.appendText("a present value matching ");
            matcher.get().describeTo(description);
        } else {
            description.appendText("<Present>");
        }
    }

    @Override
    public boolean matchesSafely(Optional<? extends T> item) {
        if (!someExpected) {
            return !item.isPresent();
        } else if (expected.isPresent()) {
            return item.isPresent() && Objects.equal(item.get(), expected.get());
        } else if (matcher.isPresent()) {
            return item.isPresent() && matcher.get().matches(item.get());
        } else {
            return item.isPresent();
        }
    }
}