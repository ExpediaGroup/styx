/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.routing.db

import com.hotels.styx.api.configuration.ObjectStore
import io.kotlintest.eventually
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldNotBeEmpty
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.milliseconds
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import reactor.test.StepVerifier
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

// We can remove AssertionError::class.java argument from the
// calls to `eventually`, after this bug fix is released:
// https://github.com/kotlintest/kotlintest/issues/753
//
class StyxObjectStoreTest : FeatureSpec() {

    init {
        feature("Retrieval") {
            scenario("Retrieves object by key") {
                val db = StyxObjectStore<String>()
                db.insert("redirect", "Record")

                db.get("redirect") shouldBe Optional.of("Record")
            }

            scenario("Returns empty if object is not found") {
                val db = StyxObjectStore<String>()

                db.get("notfound") shouldBe Optional.empty()
            }

            scenario("Retrieves all entries") {
                val db = StyxObjectStore<String>()
                db.insert("x", "x")
                db.insert("y", "y")
                db.insert("z", "z")

                db.entrySet().map { it.toPair() } shouldBe listOf(
                        "x" to "x",
                        "y" to "y",
                        "z" to "z"
                )
            }
        }

        feature("Insert") {
            scenario("Notifies watchers for any change") {
                val db = StyxObjectStore<String>()

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.insert("x", "x") }
                        .assertNext {
                            it.get("x") shouldBe Optional.of("x")
                            it.get("y") shouldBe Optional.empty()
                        }
                        .then { db.insert("y", "y") }
                        .assertNext {
                            it.get("x") shouldBe Optional.of("x")
                            it.get("y") shouldBe Optional.of("y")
                        }
                        .thenCancel()
                        .log()
                        .verify(4.seconds)

                db.watchers() shouldBe 0
            }

            scenario("Maintains database integrity in concurrent operations") {
                val db = StyxObjectStore<String>()
                val executor = newFixedThreadPool(8)

                for (i in 1..10000) {
                    executor.execute { db.insert("redirect-$i", "Record-$i") }
                }

                executor.shutdown()
                executor.awaitTermination(2, SECONDS)

                for (i in 1..10000) {
                    db.get("redirect-$i") shouldBe (Optional.of("Record-$i"))
                }
            }

            scenario("Maintains relative ordering between change and initial watch notifications") {
                val executor = Executors.newSingleThreadExecutor()

                val db = StyxObjectStore<Int>(executor)
                val events = mutableListOf<Int>()

                val watchConsumer = db.watch().toFlux().subscribe {
                    // Keeps the event notification thread busy to build up a backlog of events.
                    Thread.sleep(10)
                }

                db.insert("key", 1)
                db.insert("key", 2)

                val watcher = db.watch()
                        .toFlux()
                        .subscribe {
                            events.add(it.get("key").orElse(0xBAD_BEEF))
                        }

                db.insert("key", 3)
                db.insert("key", 4)

                watchConsumer.dispose()
                watcher.dispose()

                executor.shutdown()
                executor.awaitTermination(250, TimeUnit.MILLISECONDS)

                // Ensure the events were delivered in order
                events.fold(0) { acc, value ->
                    value.shouldNotBe(0xBAD_BEEF)
                    value.shouldBeGreaterThanOrEqual(acc)
                    value
                }
            }

            scenario("Replaces already existing object") {
                val db = StyxObjectStore<String>()

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.insert("x", "x") }
                        .assertNext {
                            it.get("x") shouldBe Optional.of("x")
                        }
                        .then { db.insert("x", "x2") }
                        .assertNext {
                            it.get("x") shouldBe Optional.of("x2")
                        }
                        .thenCancel()
                        .verify(1.seconds)

                db.get("x") shouldBe Optional.of("x2")
            }

            scenario("Returns Optional.empty, when no previous value exists") {
                val db = StyxObjectStore<String>()

                db.insert("key", "a-value") shouldBe Optional.empty()
            }

            scenario("Returns previous, replaced value") {
                val db = StyxObjectStore<String>()

                db.insert("key", "old-value") shouldBe Optional.empty()
                db.insert("key", "new-value") shouldBe Optional.of("old-value")
            }
        }

        feature("Compute") {
            scenario("Compute a new value when key doesn't already exist") {
                val db = StyxObjectStore<String>()

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.compute("key") { currentEntry -> "first value" } }
                        .assertNext { it.get("key") shouldBe Optional.of("first value") }
                        .thenCancel()
                        .verify()
            }

            scenario("Retains the existing value") {
                val db = StyxObjectStore<String>()

                db.insert("key", "old value")

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.compute("key") { currentEntry -> "old value" } }
                        .expectNoEvent(10.milliseconds)
                        .thenCancel()
                        .verify()
            }

            scenario("Replace an existing value") {
                val db = StyxObjectStore<String>()
                val latch = CountDownLatch(1)

                db.addDispatchListener("_") {
                    when (it) {
                        is ChangeNotification ->
                            if (it.snapshot.index() == 1L) {
                                latch.countDown()
                            }

                        else -> { }
                    }
                }

                db.insert("key", "old value")
                latch.await()

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.compute("key") { currentEntry -> "new value" } }
                        .assertNext { it.get("key") shouldBe Optional.of("new value") }
                        .thenCancel()
                        .verify()
            }

            scenario("Deletes value if computation returns null") {
                val db = StyxObjectStore<String>()

                db.insert("key", "old value")

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.compute("key") { currentEntry -> null } }
                        .assertNext { it.entrySet().filter { it.key === "key" }.size shouldBe 0 }
                        .thenCancel()
                        .verify()
            }

            scenario("Maintains relative ordering between change and initial watch notifications") {
                val executor = Executors.newSingleThreadExecutor()

                val db = StyxObjectStore<Int>(executor)
                val events = mutableListOf<Int>()

                val watchConsumer = db.watch().toFlux().subscribe {
                    // Keeps the event notification thread busy to build up a backlog of events.
                    Thread.sleep(10)
                }

                db.compute("key") { 1 }
                db.compute("key") { 2 }

                val watcher = db.watch()
                        .toFlux()
                        .subscribe {
                            events.add(it.get("key").orElse(0xBAD_BEEF))
                        }

                db.compute("key") { 3 }
                db.compute("key") { 4 }

                watchConsumer.dispose()
                watcher.dispose()

                executor.shutdown()
                executor.awaitTermination(250, TimeUnit.MILLISECONDS)

                // Ensure the events were delivered in order
                events.fold(0) { acc, value ->
                    value.shouldNotBe(0xBAD_BEEF)
                    value.shouldBeGreaterThanOrEqual(acc)
                    value
                }
            }
        }

        feature("Remove") {
            scenario("Removes previously stored objects") {
                val db = StyxObjectStore<String>()

                db.insert("x", "x")
                db.insert("y", "y")

                db.get("x") shouldBe Optional.of("x")
                db.get("y") shouldBe Optional.of("y")

                db.remove("x")
                db.remove("y")

                db.get("x") shouldBe Optional.empty()
                db.get("y") shouldBe Optional.empty()
            }

            scenario("Non-existent object doesn't trigger watchers") {
                val db = StyxObjectStore<String>()

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.remove("x") }
                        .expectNoEvent(500.milliseconds)
                        .thenCancel()
                        .verify()

                db.insert("y", "Y")

                StepVerifier.create(db.watch())
                        .expectNextCount(1)
                        .then { db.remove("x") }
                        .expectNoEvent(500.milliseconds)
                        .thenCancel()
                        .verify()
            }

            scenario("Notifies watchers") {
                val db = StyxObjectStore<String>()
                val watchEvents = CopyOnWriteArrayList<ObjectStore<String>>()

                db.insert("x", "x")
                db.insert("y", "y")

                Flux.from(db.watch()).subscribe { watchEvents.add(it) }
                eventually(1.seconds, java.lang.AssertionError::class.java) {
                    watchEvents.size shouldBeGreaterThanOrEqual 1
                    watchEvents.last().get("x") shouldBe Optional.of("x")
                }

                db.remove("x")
                db.remove("y")

                eventually(1.seconds, java.lang.AssertionError::class.java) {
                    watchEvents.last()["x"] shouldBe Optional.empty()
                    watchEvents.last()["y"] shouldBe Optional.empty()
                }
            }

            scenario("Maintains database integrity in concurrent operations") {
                val db = StyxObjectStore<String>()

                // Populate database with data:
                for (i in 1..10000) {
                    db.insert("redirect-$i", "Record-$i")
                }

                // Then remove everyting, concurrently:
                val executor = newFixedThreadPool(8)
                for (i in 1..10000) {
                    executor.execute { db.remove("redirect-$i") }
                }

                executor.shutdown()
                executor.awaitTermination(2, SECONDS)

                db.entrySet().shouldBeEmpty()
            }

            scenario("Maintains relative ordering between change and initial watch notifications") {
                val executor = Executors.newSingleThreadExecutor()

                val db = StyxObjectStore<Int>(executor)
                db.insert("key-01", 1)
                db.insert("key-02", 2)
                db.insert("key-03", 3)
                db.insert("key-04", 4)


                val events = mutableListOf<Long>()

                val watchConsumer = db.watch().toFlux().subscribe {
                    // Keeps the event notification thread busy to build up a backlog of events.
                    Thread.sleep(10)
                }

                db.remove("key-01")
                db.remove("key-02")

                val watcher = db.watch()
                        .toFlux()
                        .subscribe {
                            events.add(it.index())
                        }

                db.remove("key-03")
                db.remove("key-04")

                watchConsumer.dispose()
                watcher.dispose()

                executor.shutdown()
                executor.awaitTermination(250, TimeUnit.MILLISECONDS)

                // Ensure the events were delivered in order
                events.fold(0L) { previous, index ->
                    index.shouldBeGreaterThanOrEqual(previous)
                    index
                }
            }

            scenario("Returns Optional.empty, when previous value doesn't exist") {
                val db = StyxObjectStore<String>()

                db.remove("key") shouldBe Optional.empty()
            }

            scenario("Returns previous, replaced value") {
                val db = StyxObjectStore<String>()

                db.insert("key", "a-value") shouldBe Optional.empty()

                db.remove("key") shouldBe Optional.of("a-value")
            }

        }

        feature("Watch") {
            scenario("Publishes an immutable final state snapshot") {
                val db = StyxObjectStore<String>()
                val watchEvents = CopyOnWriteArrayList<ObjectStore<String>>()

                val watcher = db.watch().toFlux().subscribe { watchEvents.add(it) }

                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.isNotEmpty().shouldBeTrue()
                    watchEvents[0].get("x") shouldBe Optional.empty()
                    watchEvents[0].get("y") shouldBe Optional.empty()
                }

                db.insert("x", "x")
                db.insert("y", "y")

                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.last()["x"].isPresent.shouldBeTrue()
                    watchEvents.last()["y"].isPresent.shouldBeTrue()
                }

                watcher.dispose()
                db.watchers() shouldBe 0
            }

            scenario("Supports multiple watchers") {
                for (x in 0..100) {
                    val db = StyxObjectStore<String>()
                    val watchEvents1 = CopyOnWriteArrayList<ObjectStore<String>>()
                    val watchEvents2 = CopyOnWriteArrayList<ObjectStore<String>>()

                    val watcher1 = Flux.from(db.watch()).subscribe { watchEvents1.add(it) }
                    val watcher2 = Flux.from(db.watch()).subscribe { watchEvents2.add(it) }

                    // Wait for the initial watch event ...
                    eventually(1.seconds, java.lang.AssertionError::class.java) {
                        watchEvents1.size shouldBe 1
                        watchEvents1[0].get("x") shouldBe Optional.empty()

                        watchEvents2.size shouldBe 1
                        watchEvents2[0].get("x") shouldBe Optional.empty()
                    }

                    db.insert("x", "x")
                    db.insert("y", "y")

                    // ... otherwise we aren't guaranteed what events are going show up.
                    //
                    // The ordering between initial watch event in relation to objectStore.inserts are
                    // non-deterministic.
                    eventually(1.seconds, AssertionError::class.java) {
                        watchEvents1.last()["x"].isPresent.shouldBeTrue()
                        watchEvents1.last()["y"].isPresent.shouldBeTrue()

                        watchEvents1.last()["x"].shouldBe(Optional.of("x"))
                        watchEvents1.last()["y"].shouldBe(Optional.of("y"))
                    }

                    watcher1.dispose()
                    db.watchers() shouldBe 1

                    watcher2.dispose()
                    db.watchers() shouldBe 0
                }
            }

            scenario("Provides current snapshot at subscription") {
                val db = StyxObjectStore<String>()
                val watchEvents = CopyOnWriteArrayList<ObjectStore<String>>()

                db.insert("x", "x")

                Flux.from(db.watch()).subscribe {
                    watchEvents.add(it)
                }

                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.isNotEmpty().shouldBeTrue()
                    watchEvents[0].get("x") shouldBe Optional.of("x")
                }
            }

            scenario("Snapshot provides all entries") {
                val db = StyxObjectStore<String>()
                val watchEvents = CopyOnWriteArrayList<ObjectStore<String>>()

                db.insert("x", "payload-x")
                db.insert("y", "payload-y")

                Flux.from(db.watch()).subscribe {
                    watchEvents.add(it)
                }

                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.shouldNotBeEmpty()
                    watchEvents.last()
                            .entrySet()
                            .map { it.toPair() }
                            .let {
                                it shouldBe listOf(
                                        "x" to "payload-x",
                                        "y" to "payload-y")
                            }
                }

                db.remove("y")
                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.shouldNotBeEmpty()
                    watchEvents.last()
                            .entrySet()
                            .map { it.toPair() }
                            .let {
                                it shouldBe listOf(
                                        "x" to "payload-x")
                            }
                }

                db.insert("z", "payload-z")
                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents.shouldNotBeEmpty()
                    watchEvents.last()
                            .entrySet()
                            .map { it.toPair() }
                            .let {
                                it shouldBe listOf(
                                        "x" to "payload-x",
                                        "z" to "payload-z")
                            }
                }
            }
        }
    }

}
