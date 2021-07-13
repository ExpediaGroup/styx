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
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Styx Route Database.
 */

class StyxObjectStore<T> internal constructor(executor: ExecutorService): ObjectStore<T> {
    private val objects: AtomicReference<IndexedSnapshot<T>> = AtomicReference(
            IndexedSnapshot(0, HashTreePMap.empty()))

    private val watchers = CopyOnWriteArrayList<ChangeWatcher<T>>()
    private val notificationQueue = NotificationQueue(watchers, executor)

    companion object {
        private val sharedExecutor = Executors.newSingleThreadExecutor()
    }

    constructor(): this(sharedExecutor)

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
        return Optional.ofNullable(objects().snapshot.get(name))
    }

    /**
     * Retrieves all entries.
     */
    override fun entrySet(): Collection<Map.Entry<String, T>> = entrySet(objects.get().snapshot)

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
    fun insert(key: String, payload: T): Optional<T> {
        require(key.isNotEmpty()) { "ObjectStore insert: empty keys are not allowed." }

        var current = objects.get()
        var new = current.map { it.plus(key, payload) }

        while (!objects.compareAndSet(current, new)) {
            current = objects.get()
            new = current.map { it.plus(key, payload) }
        }

        notificationQueue.publishChange(new)

        return Optional.ofNullable(current.snapshot[key])
    }

    /**
     * Insert an object in the database that is a result of a function call.
     *
     * This method can be used to insert a new object, or to conditionally modify
     * a previously stored object.  This method takes two arguments: `key` and
     * `computation`.
     *
     * The `key` refers to the object name of interest. The `computation` provides
     * a new or modified object to be stored under `key`.
     *
     * `computation` is called with an existing object as its only argument, or
     * `null` if no objects are stored against `key`. An object returned from
     * `computation` is stored in the database.
     *
     * The operation is considered a no-op when `computation` returns back the
     * already existing object instance. In this case the existing object remains
     * in the database, and watchers are not notified.
     *
     * When `computation` returns a different instance, the new object replaces
     * the existing version and the watchers will be notified. This is considered
     * a modification.
     *
     * Note that the `computation` must always return a value. It cannot return
     * `null`, and therefore cannot be used to remove existing objects.
     *
     * @property key object name
     * @property computation a function that produces the new value
     * @return the previous value
     */
    fun compute(key: String, computation: (T?) -> T?): Optional<T> {
        require(key.isNotEmpty()) { "ObjectStore compute: empty keys are not allowed." }

        var current: IndexedSnapshot<T>
        var result: T?
        var new: IndexedSnapshot<T>

        do {
            current = objects.get()
            val existingValue = current.snapshot.get(key)
            result = computation(existingValue)

            new = if (existingValue !== null && result === null) {
                //New value is null, removing key
                current.map { it.minus(key) }
            } else if (result != existingValue) {
                // Consumer REPLACES an existing value or ADDS a new value
                current.map { it.plus(key, result) }
            } else {
                // Consumer KEEPS the existing value
                current
            }
        } while (!objects.compareAndSet(current, new))

        if (current != new) {
            // Notify only if content changed:
            notificationQueue.publishChange(new)
        }

        return Optional.ofNullable(current.snapshot[key])
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
     * @return the removed value
     */
    fun remove(key: String): Optional<T> {
        var current = objects.get()
        var new = current.map { it.minus(key) }

        // Unnecessarily increments the index when "key" doesn't exist:
        // We will live with this for now.
        while (!objects.compareAndSet(current, new)) {
            current = objects.get()
            new = current.map { it.minus(key) }
        }

        if (current.snapshot != new.snapshot) {
            notificationQueue.publishChange(new)
        }

        return Optional.ofNullable(current.snapshot[key])
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

            notificationQueue.publishInitialWatch(sink)
        }
    }

    internal fun watchers() = watchers.size

    private fun objects() = objects.get()

    override fun index() = objects.get().index

    internal fun addDispatchListener(key: String, listener: DispatchListener<T>) {
        notificationQueue.addDispatchListener(key, listener)
    }

    internal fun removeDispatchListener(key: String) {
        notificationQueue.removeDispatchListener(key)
    }

}