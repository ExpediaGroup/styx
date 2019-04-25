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

import com.hotels.styx.api.configuration.ObjectStoreSnapshot
import org.pcollections.HashTreePMap
import org.pcollections.PMap
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Styx Route Database.
 */

private interface MyListener<T> : Consumer<ObjectStoreSnapshot<T>>

class StyxObjectStore<T> : ObjectStoreSnapshot<T> {
    private val objects: AtomicReference<PMap<String, Record<T>>> = AtomicReference(HashTreePMap.empty())

    private val listeners = CopyOnWriteArrayList<MyListener<T>>()

    override fun get(name: String?): Optional<T> {
        return Optional.ofNullable(objects.get().get(name))
                .map { it.payload }
    }

    fun insert(key: String, record: T) {
        insert(key, setOf(), record)
    }

    /**
     * Returns a Publisher that emits an event for any modification (insert/remove).
     *
     * TODO: Should return a database snapshot?
     *       Should return a change indication (insert "foo", remove "bar")?
     *
     * Watch activates on subscription only.
     * Watch removed on unsubscription.
     */
    fun watch(): Publisher<ObjectStoreSnapshot<T>> {
        return Flux.push { sink ->
            val listener = object : MyListener<T> {
                override fun accept(objectStoreSnapshot: ObjectStoreSnapshot<T>) {
                    sink.next(objectStoreSnapshot)
                }
            }

            listeners.add(listener)

            sink.onCancel {
                listeners.remove(listener)
            }

            sink.onDispose {
                listeners.remove(listener)
            }
        }
    }

    fun watchers() = listeners.size

    private fun insert(key: String, tags: Set<String>, payload: T) {
        val objectsV2 = objects.get().plus(key, Record(key, tags, payload))
        objects.set(objectsV2)

        listeners.forEach { listener -> listener.accept({
            key -> Optional
                .ofNullable(objectsV2.get(key))
                .map { it.payload } }
        )}

    }


    data class Record<T>(val key: String, val tags: Set<String>, val payload: T)
}
