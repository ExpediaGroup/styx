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
package com.hotels.styx.routing.db;

import com.hotels.styx.api.configuration.ObjectStore
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Styx Route Database.
 */
class StyxObjectStore<T> : ObjectStore<T> {
    private val objects = ConcurrentHashMap<String, Record<T>>();

    override fun get(name: String?): Optional<T> {
        return Optional.ofNullable(objects.get(name))
                .map { it.payload }
    }

    private fun insert(key: String, tags: Set<String>, payload: T) {
        objects.put(key, Record(key, tags, payload))
    }

    override fun insert(key: String, record: T) {
        insert(key, setOf(), record)
    }

    data class Record<T>(val key: String, val tags:Set<String>, val payload: T)
}
