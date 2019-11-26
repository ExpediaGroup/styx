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

/*
 * Common Tag Operations:
 */

sealed class CommonValueTag<T>(
        val name: String,
        val encode: (T) -> String?,
        val decode: (String) -> T?) {

    fun valuePart(tag: String) = if (tag.startsWith("$name=")) {
        tag.removePrefix("$name=")
    } else {
        null
    }

    fun match(tag: String) = tag.startsWith("$name=")

    fun valueOf(tag: String) = valuePart(tag)
            ?.let { decode(it) }

    fun find(tags: Set<String>) = tags.firstOrNull { this.match(it) }
            ?.let { valuePart(it) }
            ?.let { this.decode(it) }
}


class NullableValueTag<T>(
        name: String,
        encode: (T) -> String?,
        decode: (String) -> T?) : CommonValueTag<T>(name, encode, decode) {

    operator fun invoke(value: T): String? = encode(value)
            ?.let {
                "$name=$it"
            }
}

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


//fun Set<String>.contains(tag: CommonValueTag<*>) = this.firstOrNull { tag.match(it) } != null

// TODO: Compare to ObjectTag.find()
fun <T> Set<String>.valueOf(tag: CommonValueTag<T>): T? = this.firstOrNull { tag.match(it) }
        ?.let { tag.valuePart(it) }
        ?.let { tag.decode(it) }

// TODO: Compare to ObjectTag.valueOf():
fun <T> String.match(tag: CommonValueTag<T>) = tag.valuePart(this)
        ?.let { tag.decode(it) }

fun String.isA(tag: CommonValueTag<*>) = tag.match(this)
