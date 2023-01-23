/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.common


/**
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (whether its *preconditions* have been met).
 *
 * Simplifies Java code. Not really necessary for Kotlin.
 */
object Preconditions {
    /**
     * Ensures that the string passed as a parameter to the calling method is not null or empty.
     *
     * @param value a string
     * @return the same string if non-empty
     * @throws NullPointerException if `value` is null or empty
     */
    @JvmStatic
    fun checkNotEmpty(value: String?): String {
        require(!value.isNullOrEmpty())
        return value
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * @param reference  a reference to pass through if the `expression` is true
     * @param expression a boolean expression
     * @param <T>        reference type
     * @return the same reference passed in (if `expression` is true)
     * @throws IllegalArgumentException if `expression` is false
    </T> */
    @JvmStatic
    fun <T> checkArgument(reference: T, expression: Boolean): T {
        require(expression)
        return reference
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @throws IllegalArgumentException if `expression` is false
     */
    @JvmStatic
    fun checkArgument(expression: Boolean) {
        require(expression)
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     * string using [String.valueOf]
     * @throws IllegalArgumentException if `expression` is false
     */
    @JvmStatic
    fun checkArgument(expression: Boolean, errorMessage: Any) {
        require(expression) { errorMessage.toString() }
    }

    /**
     * Ensures the truth of an expression involving one or more parameters to the calling method.
     *
     * This method is originally sourced from Guava.
     *
     * @param expression a boolean expression
     * @param errorMessageTemplate a template for the exception message should the check fail. The
     * message is formed by replacing each `%s` placeholder in the template with an
     * argument. These are matched by position - the first `%s` gets `errorMessageArgs[0]`, etc.  Unmatched arguments will be appended to the formatted message
     * in square braces. Unmatched placeholders will be left as-is.
     * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
     * are converted to strings using [String.valueOf].
     * @throws IllegalArgumentException if `expression` is false
     * @throws NullPointerException if the check fails and either `errorMessageTemplate` or
     * `errorMessageArgs` is null (don't let this happen)
     */
    @JvmStatic
    fun checkArgument(
        expression: Boolean,
        errorMessageTemplate: String,
        vararg errorMessageArgs: Any?
    ) {
        require(expression) { String.format(errorMessageTemplate, *errorMessageArgs) }
    }
}
