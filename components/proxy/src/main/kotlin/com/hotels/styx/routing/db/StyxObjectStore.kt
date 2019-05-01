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
import org.pcollections.HashTreePMap
import org.pcollections.PMap
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Styx Route Database.
 */
class StyxObjectStore<T> : ObjectStore<T> {
    private val objects: AtomicReference<PMap<String, Record<T>>> = AtomicReference(HashTreePMap.empty())
    private val watchers = CopyOnWriteArrayList<ChangeWatcher<T>>()

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }

    /**
     * Retrieves an object from this object store.
     *
     * Returns `Optional.empty` when an object with `key` doesn't exist.
     *
     * This method is thread safe. It can be called simultaneously from many threads.
     *
     * @property key object name
     * @return an optional object
     */

    override fun get(name: String): Optional<T> {
        return Optional.ofNullable(objects().get(name))
                .map { it.payload }
    }

    /**
     * Inserts a new object in object store.
     *
     * If an object already exist with same `key`, then the previous object is
     * replaced with new one.
     *
     * Notifies watchers.
     *
     * This method is thread safe. It can be called simultaneously from many threads.
     *
     * @property key object name
     * @property payload the object itself
     */
    fun insert(key: String, payload: T) {
        insert(key, setOf(), payload)
    }

    /**
     * Removes an object from this object store.
     *
     * Watchers are notified after successful removal.
     *
     * If `key` doesn't exist, then nothing happens and watchers are not notified.
     *
     * This method is thread safe. It can be called simultaneously from many threads.
     *
     * @property key object name
     * @property payload the object itself
     */
    fun remove(key: T) {
        queue {
            val nextVersion = objects().minus(key)

            if (nextVersion != objects()) {
                objects.set(nextVersion)
                notifyWatchers(nextVersion)
            }
        }
    }

    /**
     * Returns a Publisher that emits an event at any modification.
     *
     * Watch activates on subscription only.
     * Watch removed on unsubscription.
     */
    fun watch(): Publisher<ObjectStore<T>> {
        return Flux.push { sink ->
            val watcher: ChangeWatcher<T> = { sink.next(it) }

            sink.onDispose {
                watchers.remove(watcher)
            }

            watchers.add(watcher)
            queue {
                emitInitialSnapshot(sink)
            }
        }
    }

    private fun emitInitialSnapshot(sink: FluxSink<ObjectStore<T>>) {
        sink.next(ObjectStore { key ->
            Optional
                    .ofNullable(objects().get(key))
                    .map { it.payload }
        })
    }

    internal fun watchers() = watchers.size

    private fun objects() = objects.get()

    private fun queue(task: () -> Unit) {
        executor.submit(task)
    }

    private fun insert(key: String, tags: Set<String>, payload: T) {
        queue {
            val nextVersion = objects().plus(key, Record(key, tags, payload))
            objects.set(nextVersion)
            notifyWatchers(nextVersion)
        }
    }

    private fun notifyWatchers(objectsV2: PMap<String, Record<T>>) {
        watchers.forEach { listener ->
            listener.invoke(snapshot(objectsV2))
        }
    }

    private fun snapshot(snapshot: PMap<String, Record<T>>) = ObjectStore<T> { key: String ->
        Optional.ofNullable(snapshot.get(key))
                .map { it.payload }
    }
}

private typealias ChangeWatcher<T> = (ObjectStore<T>) -> Unit

private data class Record<T>(val key: String, val tags: Set<String>, val payload: T)
