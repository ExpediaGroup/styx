/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.javaconvenience

import java.util.stream.Stream
import kotlin.streams.asStream

/*
 * Note: although written in Kotlin, these convenience methods are intended to be used by Java code.
 * As such, certain language features that make Kotlin development easier will have no benefit in Java (or possibly make things more complicated),
 * for example, extension methods, this-receivers.
 */

fun <T> iteratorToList(iterator: Iterator<T>): List<T> = Iterable {
    iterator
}.toList()

fun <T> iterableToList(iterable: Iterable<T>): List<T> = iterable.toList()

fun <T> iteratorToStream(iterator: Iterator<T>): Stream<T> = iterator.asSequence().asStream()

fun <K, V> merge(vararg maps: Map<K, V>): Map<K, V> {
    val map = HashMap<K, V>()
    maps.forEach {
        map.putAll(it)
    }
    return map
}

fun <T> first(iterable: Iterable<T>): T? = iterable.iterator().nonEmpty?.next()

// Private functions can use whatever Kotlin features as Java code will not see them.

private val <T> Iterator<T>.nonEmpty: Iterator<T>? get() = takeIf { it.hasNext() }