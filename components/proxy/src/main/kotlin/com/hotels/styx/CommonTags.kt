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
package com.hotels.styx

/**
 * A common tag representation.
 *
 * Enforces a common tag format and provides a set of higher order functionality
 * that operate on provided `name`, `encode` and `decode` properties.
 *
 * A tag string is a name-value pair separated by equal ('=') sign:
 *
 *     tag-string = name "=" value-part
 *
 * `encode` and `decode` are functions to encode a T to a value-part string,
 * and to decode the value-part string back to T.
 *
 * @property name a tag name
 * @property encode a function that encodes a value of T as a tag value string
 * @property decode a function that decodes a tag value string as a T
 */
sealed class CommonValueTag<T>(
        val name: String,
        val encode: (T) -> String?,
        val decode: (String) -> T?) {

    /**
     * Extracts value part (the right hand side) from a tag string.
     *
     * @param tag a tag string
     * @return the tag value
     */
    fun valuePart(tag: String) = if (tag.startsWith("$name=")) {
        tag.removePrefix("$name=")
    } else {
        null
    }

    /**
     * Tests if a given string matches this tag. A match is positive when the string
     * starts with `name` followed by `=`.
     *
     * @param a tag string
     * @return True if this string is possibly a matching tag. Otherwise return false.
     */
    fun match(tag: String) = tag.startsWith("$name=")

    /**
     * Decodes given tag string to its typed value.
     *
     * @param tag a tag string
     * @return a decoded tag value, or null if decoding failed
     */
    fun valueOf(tag: String) = valuePart(tag)
            ?.let { decode(it) }

    /**
     * Find this tag from a set of tag strings. If found, decode the tag value. Return null if
     * tag was not found, or if decoding failed.
     *
     * @param tags a set of tag strings
     * @return A decoded value if tag was found. Otherwise return null.
     */
    fun find(tags: Set<String>) = tags.firstOrNull { this.match(it) }
            ?.let { valuePart(it) }
            ?.let { this.decode(it) }

    /**
     * Removes all instances of this tag from a set of tag strings.
     * Return a new Set<String> with all instances of this tag removed.
     *
     * @param tags a set of tag strings
     * @return A new set of strings without this tag.
     */
    fun remove(tags: Set<String>) = tags
            .filterNot { this.match(it) }
            .toSet()
}

/**
 * A NullableValueTag invoke method returns null when value cannot be encoded to string.
 * The API consumer must handle this situation.
 */
class NullableValueTag<T>(
        name: String,
        encode: (T) -> String?,
        decode: (String) -> T?) : CommonValueTag<T>(name, encode, decode) {

    operator fun invoke(value: T): String? = encode(value)
            ?.let {
                "$name=$it"
            }
}

/**
 * A SafeValueTag invoke method throws a KotlinNullPointerException when the
 * tag value cannot be encoded to string.
 */
class SafeValueTag<T>(
        name: String,
        encode: (T) -> String?,
        decode: (String) -> T?) : CommonValueTag<T>(name, encode, decode) {

    operator fun invoke(value: T): String = encode(value)
            .let {
                it!!
                "$name=$it"
            }
}

fun <T> String.match(tag: CommonValueTag<T>) = tag.valuePart(this)
        ?.let { tag.decode(it) }
