/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import static com.google.common.base.Strings.isNullOrEmpty;

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
    public static String checkNotEmpty(String value, String message) {
        checkArgument(value, !isNullOrEmpty(value), message);
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
    public static <T> T checkArgument(T reference, boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
        return reference;
    }

    private MorePreconditions() {
    }
}
