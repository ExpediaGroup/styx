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

import com.google.common.base.Preconditions;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (whether its <i>preconditions</i> have been met).
 */
public final class MorePreconditions {
    /**
     * Ensures that the string passed as a parameter to the calling method is not null or empty.
     *
     * @param value a string
     * @return the same string if non-empty
     * @throws NullPointerException if {@code value} is null or empty
     */
    public static String checkNotEmpty(String value) {
        Preconditions.checkArgument(!isNullOrEmpty(value));
        return value;
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * @param reference  a reference to pass through if the {@code expression} is true
     * @param expression a boolean expression
     * @param <T>        reference type
     * @return the same reference passed in (if {@code expression} is true)
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static <T> T checkArgument(T reference, boolean expression) {
        Preconditions.checkArgument(expression);
        return reference;
    }


    /**
     * Ensures that an object reference passed as a parameter to the calling method satisfies
     * the condition specified by the matcher.
     *
     * @param reference an object reference
     * @param condition the condition to be satisfied
     * @param <T>        reference type
     * @return the same reference passed in
     * @throws IllegalArgumentException if {@code condition} is false
     */
    public static <T> T checkArgument(T reference, Matcher<T> condition) {
        return checkArgument(reference, condition, "argument");
    }

    /**
     * Ensures that an object reference passed as a parameter to the calling method satisfies
     * the condition specified by the matcher.
     *
     * @param reference     an object reference
     * @param condition     the condition to be satisfied
     * @param referenceName the name of the argument to use in the error description
     * @param <T>        reference type
     * @return the same reference passed in
     * @throws IllegalArgumentException if {@code condition} is false
     */
    public static <T> T checkArgument(T reference, Matcher<T> condition, String referenceName) {
        if (!condition.matches(reference)) {
            Description description = new StringDescription();
            description.appendText(referenceName)
                    .appendText("\nExpected: ")
                    .appendDescriptionOf(condition)
                    .appendText("\n     but: ");
            condition.describeMismatch(reference, description);
            throw new IllegalArgumentException(description.toString());
        }
        return reference;
    }

    /**
     * A matcher that checks whether an integer is between a given minimum and maximum (inclusive).
     *
     * @param minimum minimum value (inclusive)
     * @param maximum maximum value (inclusive)
     * @return the matcher
     */
    public static Matcher<Integer> inRange(int minimum, int maximum) {
        return allOf(greaterThanOrEqualTo(minimum), lessThanOrEqualTo(maximum));
    }

    private MorePreconditions() {
    }
}
