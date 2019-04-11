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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.util.Optional

class StyxObjectStoreTest : FeatureSpec() {

    init {
        feature("Get") {
            scenario("Retrieves a stored object") {
                val db = StyxObjectStore<String>()
                db.insert("redirect", "Record")

                db.get("redirect") shouldBe Optional.of("Record")
            }

            scenario("Returns empty if object is not found") {
                val db = StyxObjectStore<String>()

                db.get("notfound") shouldBe Optional.empty()
            }
        }

        feature("Get tags") {
            val db = StyxObjectStore<String>()
            db.insert("one", setOf("a", "b"), "One")
            db.insert("two", setOf("a", "c"), "Two")
            db.insert("three", setOf("b", "c"), "Three")

            scenario("Retrieves all matching objects") {
                assertThat(db.getAll(setOf("a")), equalTo(setOf("One", "Two")))
                assertThat(db.getAll(setOf("b")), equalTo(setOf("One", "Three")))
                assertThat(db.getAll(setOf("NA")), equalTo(setOf()))
            }

            scenario("Retrieves all objects matching multiple tags") {
                assertThat(db.getAll(setOf("a", "c")), equalTo(setOf("Two")))
            }
        }

        feature("Watching for changes") {
            scenario("Notifies object modifications") {
                val db = StyxObjectStore<String>()
                db.insert("one", setOf("a", "b"), "one")
            }
        }
    }

}