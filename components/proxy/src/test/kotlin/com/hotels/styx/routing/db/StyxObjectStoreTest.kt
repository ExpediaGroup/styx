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
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.Optional

class StyxObjectStoreTest : FeatureSpec() {

    init {
        feature("Retrieve") {
            scenario("A stored object") {
                val db = StyxObjectStore<String>()
                db.insert("redirect", "Record")

                db.get("redirect") shouldBe Optional.of("Record")
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

            scenario("Notifies multiple watchers") {
                val db = StyxObjectStore<String>()
                val watchEvents1 = arrayListOf<ObjectStoreSnapshot<String>>()
                val watchEvents2 = arrayListOf<ObjectStoreSnapshot<String>>()

                val watcher1 = Flux.from(db.watch()).subscribe { watchEvents1.add(it) }
                val watcher2 = Flux.from(db.watch()).subscribe { watchEvents2.add(it) }

                db.insert("x", "x")
                db.insert("y", "y")

                watchEvents1[0].get("x") shouldBe Optional.of("x")
                watchEvents2[0].get("x") shouldBe Optional.of("x")

                watchEvents1[0].get("y") shouldBe Optional.empty()
                watchEvents2[0].get("y") shouldBe Optional.empty()

                watchEvents1[1].get("x") shouldBe Optional.of("x")
                watchEvents2[1].get("x") shouldBe Optional.of("x")

                watchEvents1[1].get("y") shouldBe Optional.of("y")
                watchEvents2[1].get("y") shouldBe Optional.of("y")

                watcher1.dispose()
                db.watchers() shouldBe 1

                watcher2.dispose()
                db.watchers() shouldBe 0
            }

            scenario("Watchers receive immutable snapshot") {
                val db = StyxObjectStore<String>()
                val watchEvents = arrayListOf<ObjectStoreSnapshot<String>>()

                val watcher = Flux.from(db.watch()).subscribe { watchEvents.add(it) }

                db.insert("x", "x")
                db.insert("y", "y")

                watchEvents[0].get("x") shouldBe Optional.of("x")
                watchEvents[0].get("y") shouldBe Optional.empty()

                watchEvents[1].get("x") shouldBe Optional.of("x")
                watchEvents[1].get("y") shouldBe Optional.of("y")

                watcher.dispose()
                db.watchers() shouldBe 0
            }

//            scenario("Runs listeners synchronously") {
//                val db = StyxObjectStore<String>()
//
//                for (i in 1 .. 10) {
//                    Thread({
//                        db.insert("redirect", "Record-$i")
//                    }).start()
//                }
//
//                db.get("redirect") shouldBe(Optional.of("Record-10"))
//            }
        }
    }

}