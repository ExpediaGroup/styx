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
package com.hotels.styx

import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec

class CommonValueTagTest : FunSpec({

    val intTag = NullableValueTag(
            "intTag",
            { value -> value.toString() },
            { tagValue ->
                kotlin.runCatching { tagValue.toInt() }
                        .getOrNull()
            })


    context("remove() method") {
        test("Removes a tag") {
            intTag.remove(setOf("intTag=5", "abc=6", "def=7")).shouldBe(setOf("abc=6", "def=7"))
            intTag.remove(setOf("intTag=5")).shouldBeEmpty()
        }

        test("Removes all matching tags") {
            intTag.remove(setOf("intTag=5", "abc=6", "intTag=7", "def=7")).shouldBe(setOf("abc=6", "def=7"))
            intTag.remove(setOf("intTag=5", "intTag=7")).shouldBeEmpty()
        }

        test("Does nothing when tag is not present") {
            intTag.remove(setOf("foo=5", "abc=6", "bar=7", "def=7")).shouldBe(setOf("foo=5", "abc=6", "bar=7", "def=7"))
        }

        test("Does nothing when empty map is passed in") {
            intTag.remove(setOf()).shouldBeEmpty()
        }
    }

})