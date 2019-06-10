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
import kotlin.streams.toList

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
     * Retrieves all entries.
     */
    override fun entrySet(): Collection<Map.Entry<String, T>> = entrySet(objects.get())

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
     * @return the previous value
     */
    fun insert(key: String, payload: T) = insert(key, setOf(), payload)


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
     * @return the removed value
     */
    fun remove(key: String): Optional<T> {
        var current = objects.get()
        var new = current.minus(key)

        while (!objects.compareAndSet(current, new)) {
            current = objects.get()
            new = current.minus(key)
        }

        if (current != new) {
            queue {
                notifyWatchers(new)
            }
        }

        return Optional.ofNullable(current[key]?.payload)
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
        sink.next(snapshot(objects()))
    }

    internal fun watchers() = watchers.size

    private fun objects() = objects.get()

    private fun queue(task: () -> Unit) {
        executor.submit(task)
    }

    private fun insert(key: String, tags: Set<String>, payload: T): Optional<T> {
        var current = objects.get()
        var new = current.plus(key, Record(key, tags, payload))

        while (!objects.compareAndSet(current, new)) {
            current = objects.get()
            new = current.plus(key, Record(key, tags, payload))
        }

        queue {
            notifyWatchers(new)
        }

        return Optional.ofNullable(current[key]?.payload)
    }

    private fun notifyWatchers(objectsV2: PMap<String, Record<T>>) {
        watchers.forEach { listener ->
            listener.invoke(snapshot(objectsV2))
        }
    }

    private fun snapshot(snapshot: PMap<String, Record<T>>) = object: ObjectStore<T> {
        override fun get(key: String?): Optional<T> {
            return Optional.ofNullable(snapshot.get(key))
                    .map { it.payload }
        }

        override fun entrySet(): Collection<Map.Entry<String, T>> = entrySet(snapshot)
    }

    private fun entrySet(snapshot: PMap<String, Record<T>>) = snapshot.entries.stream()
            .map { toPayloadEntry(it) }
            .toList()

    private fun toPayloadEntry(entry: Map.Entry<String, Record<T>>): Map.Entry<String, T> = object : Map.Entry<String, T> {
        override val value: T
            get() = entry.value.payload

        override val key: String
            get() = entry.key
    }

}

private typealias ChangeWatcher<T> = (ObjectStore<T>) -> Unit

private data class Record<T>(val key: String, val tags: Set<String>, val payload: T)
