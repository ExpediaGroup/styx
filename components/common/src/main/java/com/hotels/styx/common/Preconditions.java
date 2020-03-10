/*
  Copyright (C) 2013-2020 Expedia Inc.

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

/*
 * Portions of this code (identified in the relevant Javadoc) are:
 *
 * Copyright (C) 2010 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.common;

import static java.lang.String.format;

/**
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (whether its <i>preconditions</i> have been met).
 */
public final class Preconditions {
    /**
     * Ensures that the string passed as a parameter to the calling method is not null or empty.
     *
     * @param value a string
     * @return the same string if non-empty
     * @throws NullPointerException if {@code value} is null or empty
     */
    public static String checkNotEmpty(String value) {

        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException();
        }
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
        if (!expression) {
            throw new IllegalArgumentException();
        }
        return reference;
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *     string using {@link String#valueOf(Object)}
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @param errorMessageTemplate a template for the exception message should the check fail. The
     *     message is formed by replacing each {@code %s} placeholder in the template with an
     *     argument. These are matched by position - the first {@code %s} gets {@code
     *     errorMessageArgs[0]}, etc.  Unmatched arguments will be appended to the formatted message
     *     in square braces. Unmatched placeholders will be left as-is.
     * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
     *     are converted to strings using {@link String#valueOf(Object)}.
     * @throws IllegalArgumentException if {@code expression} is false
     * @throws NullPointerException if the check fails and either {@code errorMessageTemplate} or
     *     {@code errorMessageArgs} is null (don't let this happen)
     */
    public static void checkArgument(boolean expression,
                                     String errorMessageTemplate,
                                     Object... errorMessageArgs) {
        if (!expression) {
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
        }
    }

    private Preconditions() {
    }
}
