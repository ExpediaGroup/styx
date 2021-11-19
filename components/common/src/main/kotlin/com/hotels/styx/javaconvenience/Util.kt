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

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Supplier
import java.io.InputStream
import java.io.OutputStream
import java.util.stream.Stream
import kotlin.streams.asStream

/*
 * Note: although written in Kotlin, these convenience methods are intended to be used by Java code.
 *
 * This has the following effects:
 * - Some functions seem redundant as they wrap a single kotlin function (the kotlin function doesn't exist in Java)
 * - Some language features that make Kotlin development easier are not used. For example: extension methods, this-receivers.
 */

fun <T> iteratorToList(iterator: Iterator<T>): List<T> = Iterable {
    iterator
}.toList()

fun <T> iterableToList(iterable: Iterable<T>): List<T> = iterable.toList()

/**
 * Preserves iteration order.
 */
fun <T> iterableToSet(iterable: Iterable<T>): Set<T> = iterable.toSet()

/**
 * Preserves iteration order.
 */
fun <T> arrayToSet(array: Array<T>): Set<T> = array.toSet()

fun <T> iteratorToStream(iterator: Iterator<T>): Stream<T> = iterator.asSequence().asStream()

fun <K, V> merge(vararg maps: Map<K, V>): Map<K, V> {
    val map = HashMap<K, V>()
    maps.forEach {
        map.putAll(it)
    }
    return map
}

fun <T> isEmpty(iterable: Iterable<T>): Boolean = iterable.count() == 0

fun <T> first(iterable: Iterable<T>): T? = iterable.iterator().nonEmpty?.next()

fun <T> size(iterable: Iterable<T>): Int = iterable.count()

fun <T> append(list: List<T>, elem: T): List<T> = list.plus(elem)

fun <T> append(list: List<T>, elements: Array<T>): List<T> = list.plus(elements)

fun <K, V> orderedMap(vararg entries: com.hotels.styx.common.Pair<K, V>): Map<K, V> {
    val map = LinkedHashMap<K, V>()

    entries.forEach {
        map[it.key()] = it.value()
    }

    return map
}

/**
 * Allows Java checked exception to be thrown without checking or wrapping in RuntimeException.
 * (Kotlin does not have concept of checked exceptions, so any Java exception is treated as unchecked).
 */
fun <T> uncheck(code : Callable<T>) : T = code.call()

fun copy(from: InputStream, to: OutputStream): Long = from.copyTo(to)

@JvmOverloads
fun bytes(from: InputStream, closeWhenDone : Boolean = false): ByteArray =
    if(closeWhenDone) {
        from.use { it.readAllBytes() }
    } else {
        from.readAllBytes()
    }

// Private functions can use whatever Kotlin features as Java code will not see them.

private val <T> Iterator<T>.nonEmpty: Iterator<T>? get() = takeIf { it.hasNext() }