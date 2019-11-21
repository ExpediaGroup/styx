package com.hotels.styx.routing.db

import com.hotels.styx.api.configuration.ObjectStore
import org.pcollections.HashTreePMap
import org.pcollections.PMap
import reactor.core.publisher.FluxSink
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


internal class NotificationQueue<T>(val watchers: CopyOnWriteArrayList<ChangeWatcher<T>>, val executor: ExecutorService) {
    @Volatile
    private var pendingSnapshot = IndexedSnapshot<T>(0, HashTreePMap.empty())
    @Volatile
    private var issuedSnapshot = IndexedSnapshot<T>(0, HashTreePMap.empty())
    private val pendingChangeNotification = AtomicBoolean(false)
    private val lock = ReentrantLock()

    private val listeners = ConcurrentHashMap<String, DispatchListener<T>>()

    fun publishChange(snapshot: IndexedSnapshot<T>) {
        val inQueue = lock.withLock {
            if (snapshot.index <= pendingSnapshot.index) {
                // Snapshot is older than one pending for publishing
                return
            }

            pendingSnapshot = snapshot

            pendingChangeNotification.getAndSet(true)
        }

        if (!inQueue) {
            executor.submit {
                pendingChangeNotification.set(false)

                issuedSnapshot = pendingSnapshot
                watchers.forEach { watcher ->
                    watcher.invoke(newSnapshot(pendingSnapshot))
                }

                listeners.forEach {
                    it.value.invoke(ChangeNotification(
                            newSnapshot(issuedSnapshot),
                            pendingChangeNotification.get()
                    ))
                }
            }
        }

    }

    fun publishInitialWatch(sink: FluxSink<ObjectStore<T>>) {
        executor.submit {
            sink.next(newSnapshot(issuedSnapshot))
            listeners.forEach {
                it.value.invoke(InitialWatchNotification(
                        newSnapshot(issuedSnapshot),
                        pendingChangeNotification.get()
                ))
            }
        }
    }

    internal fun addDispatchListener(key: String, listener: DispatchListener<T>) {
        listeners.put(key, listener)
    }

    internal fun removeDispatchListener(key: String) {
        listeners.remove(key)
    }

    private fun newSnapshot(snapshot: IndexedSnapshot<T>) = object : ObjectStore<T> {
        override fun get(key: String?): Optional<T> {
            return Optional.ofNullable(snapshot.snapshot[key])
        }

        override fun entrySet(): Collection<Map.Entry<String, T>> = entrySet(snapshot.snapshot)

        override fun index() = snapshot.index
    }
}

internal fun <T> entrySet(snapshot: PMap<String, T>): Collection<Map.Entry<String, T>> = snapshot.entries

internal data class IndexedSnapshot<T>(val index: Long, val snapshot: PMap<String, T>) {
    fun map(modification: (PMap<String, T>) -> PMap<String, T>) = IndexedSnapshot(this.index + 1, modification(this.snapshot))
}

internal typealias ChangeWatcher<T> = (ObjectStore<T>) -> Unit

internal typealias DispatchListener<T> = (DispatchListenerNotification<T>) -> Unit

internal sealed class DispatchListenerNotification<T>

internal data class ChangeNotification<T>(
        val snapshot: ObjectStore<T>,
        val pendingNotifications: Boolean) : DispatchListenerNotification<T>()

internal data class InitialWatchNotification<T>(
        val snapshot: ObjectStore<T>,
        val pendingNotifications: Boolean) : DispatchListenerNotification<T>()
