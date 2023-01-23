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

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function


/**
 * Simple cache. Concurrent. No invalidation.
 *
 * @param <K> key type
 * @param <V> value type
 */
class SimpleCache<K, V>(private val generator: Function<K, V>) {
    private val map = ConcurrentHashMap<K, V>()

    operator fun get(key: K): V = map.computeIfAbsent(key, generator)

    override fun toString(): String = map.toString()
}
