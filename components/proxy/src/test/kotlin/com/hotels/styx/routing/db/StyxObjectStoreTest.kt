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
package com.hotels.styx.routing.db

import com.hotels.styx.api.configuration.ObjectStoreSnapshot
import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// We can remove AssertionError::class.java argument from the
// calls to `eventually`, after this bug fix is released:
// https://github.com/kotlintest/kotlintest/issues/753
//
class StyxObjectStoreTest : FeatureSpec() {

    init {
        feature("Retrieve") {
            scenario("A stored object") {
                val db = StyxObjectStore<String>()
                db.insert("redirect", "Record")

                eventually(1.seconds, AssertionError::class.java) {
                    db.get("redirect") shouldBe Optional.of("Record")
                }
            }

            scenario("Returns empty if object is not found") {
                val db = StyxObjectStore<String>()

                db.get("notfound") shouldBe Optional.empty()
            }
        }

        feature("Insert") {
            scenario("Notifies watchers for any change") {
                val db = StyxObjectStore<String>()

                StepVerifier.create<ObjectStoreSnapshot<String>>(db.watch())
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
                        .verify(4.seconds)

                db.watchers() shouldBe 0
            }

            scenario("Maintains database integrity in concurrent operations") {
                val db = StyxObjectStore<String>()
                val executor = Executors.newFixedThreadPool(4)

                for (i in 1 .. 1000) {
                    executor.execute { db.insert("redirect-$i", "Record-$i") }
                }

                executor.shutdown()
                executor.awaitTermination(2, TimeUnit.SECONDS)

                eventually(1.seconds, AssertionError::class.java) {
                    for (i in 1..1000) {
                        db.get("redirect-$i") shouldBe (Optional.of("Record-$i"))
                    }
                }
            }
        }

        feature("Watch") {
            scenario("Supports multiple watchers") {
                val db = StyxObjectStore<String>()
                val watchEvents1 = arrayListOf<ObjectStoreSnapshot<String>>()
                val watchEvents2 = arrayListOf<ObjectStoreSnapshot<String>>()

                val watcher1 = Flux.from(db.watch()).subscribe { watchEvents1.add(it) }
                val watcher2 = Flux.from(db.watch()).subscribe { watchEvents2.add(it) }

                // Wait for the initial watch event ...
                eventually(1.seconds, java.lang.AssertionError::class.java) {
                    watchEvents1[0].get("x") shouldBe Optional.empty()
                    watchEvents2[0].get("x") shouldBe Optional.empty()
                }

                db.insert("x", "x")
                db.insert("y", "y")

                // ... otherwise we aren't guaranteed what events are going show up.
                //
                // The ordering between initial watch event in relation to db.inserts are
                // non-deterministic.
                eventually(1.seconds, AssertionError::class.java) {
                    watchEvents1[1].get("x") shouldBe Optional.of("x")
                    watchEvents2[1].get("x") shouldBe Optional.of("x")

                    watchEvents1[1].get("y") shouldBe Optional.empty()
                    watchEvents2[1].get("y") shouldBe Optional.empty()

                    watchEvents1[2].get("x") shouldBe Optional.of("x")
                    watchEvents2[2].get("x") shouldBe Optional.of("x")

                    watchEvents1[2].get("y") shouldBe Optional.of("y")
                    watchEvents2[2].get("y") shouldBe Optional.of("y")
                }

                watcher1.dispose()
                db.watchers() shouldBe 1

                watcher2.dispose()
                db.watchers() shouldBe 0
            }

            scenario("Provides immutable snapshot") {
                val db = StyxObjectStore<String>()
                val watchEvents = arrayListOf<ObjectStoreSnapshot<String>>()

                val watcher = Flux.from(db.watch()).subscribe { watchEvents.add(it) }

                eventually (1.seconds, AssertionError::class.java) {
                    watchEvents[0].get("x") shouldBe Optional.empty()
                    watchEvents[0].get("y") shouldBe Optional.empty()
                }

                db.insert("x", "x")
                db.insert("y", "y")

                eventually (1.seconds, AssertionError::class.java) {
                    watchEvents[1].get("x") shouldBe Optional.of("x")
                    watchEvents[1].get("y") shouldBe Optional.empty()

                    watchEvents[2].get("x") shouldBe Optional.of("x")
                    watchEvents[2].get("y") shouldBe Optional.of("y")
                }

                watcher.dispose()
                db.watchers() shouldBe 0
            }

            scenario("Provides current snapshot at subscription") {
                val db = StyxObjectStore<String>()
                val watchEvents = arrayListOf<ObjectStoreSnapshot<String>>()

                db.insert("x", "x")

                Flux.from(db.watch()).subscribe() {
                    watchEvents.add(it)
                }

                eventually (1.seconds, AssertionError::class.java) {
                    watchEvents[0].get("x") shouldBe Optional.of("x")
                }
            }
        }
    }

}