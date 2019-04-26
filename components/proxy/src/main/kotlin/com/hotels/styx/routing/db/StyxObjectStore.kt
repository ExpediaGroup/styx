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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Styx Route Database.
 */
class StyxObjectStore<T> : ObjectStoreSnapshot<T> {
    private val objects: AtomicReference<PMap<String, Record<T>>> = AtomicReference(HashTreePMap.empty())
    private val watchers = CopyOnWriteArrayList<ChangeWatcher<T>>()
    private val executor = Executors.newSingleThreadExecutor()

    override fun get(name: String): Optional<T> {
        return Optional.ofNullable(objects().get(name))
                .map { it.payload }
    }

    fun insert(key: String, record: T) {
        queue {
            insert(key, setOf(), record)
        }
    }

    /**
     * Returns a Publisher that emits an event at any modification.
     *
     * Watch activates on subscription only.
     * Watch removed on unsubscription.
     */
    fun watch(): Publisher<ObjectStoreSnapshot<T>> {
        return Flux.push { sink ->
            val watcher = object : ChangeWatcher<T> {
                override fun accept(objectStoreSnapshot: ObjectStoreSnapshot<T>) {
                    sink.next(objectStoreSnapshot)
                }
            }

            sink.onDispose {
                watchers.remove(watcher)
            }

            watchers.add(watcher)

            queue {
                sink.next(ObjectStoreSnapshot { key ->
                    Optional
                            .ofNullable(objects().get(key))
                            .map { it.payload }
                })
            }
        }
    }

    fun watchers() = watchers.size

    private fun insert(key: String, tags: Set<String>, payload: T) {
        val objectsV2 = objects().plus(key, Record(key, tags, payload))
        objects.set(objectsV2)

        watchers.forEach { listener ->
            listener.accept({ key ->
                Optional
                        .ofNullable(objectsV2.get(key))
                        .map { it.payload }
            })
        }
    }

    private fun objects() = objects.get()

    private fun queue(task: () -> Unit) {
        executor.submit(task)
    }

}

private interface ChangeWatcher<T> : Consumer<ObjectStoreSnapshot<T>>
private data class Record<T>(val key: String, val tags: Set<String>, val payload: T)
