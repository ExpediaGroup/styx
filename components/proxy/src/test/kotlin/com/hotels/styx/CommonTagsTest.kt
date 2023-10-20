/*
  Copyright (C) 2013-2023 Expedia Inc.

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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CommonTagsTest : FunSpec({

    val intTag = NullableValueTag(
            "intTag",
            { value -> value.toString() },
            { tagValue ->
                kotlin.runCatching { tagValue.toInt() }
                        .getOrNull()
            })

    context("invoke() method") {
        test("Creates a new tag string from a valid input") {
            intTag(5) shouldBe "intTag=5"
            intTag(-55) shouldBe "intTag=-55"
        }
    }

    context("valueOf() method") {
        test("Decodes a value from a valid tag string") {
            intTag.valueOf("intTag=99") shouldBe 99
            intTag.valueOf("intTag=-99") shouldBe -99
        }

        test("Returns null for non-conforming tag strings") {
            intTag.valueOf("").shouldBeNull()
            intTag.valueOf("intT").shouldBeNull()
            intTag.valueOf("intTag").shouldBeNull()
            intTag.valueOf("intTag=").shouldBeNull()
            intTag.valueOf("intTag=abc").shouldBeNull()
            intTag.valueOf("=abc").shouldBeNull()
            intTag.valueOf("=").shouldBeNull()
        }
    }

    context("find() method") {
        test("Returns the first found tag value") {
            intTag.find(setOf("intTag=99")) shouldBe 99
            intTag.find(setOf("abc", "blah=", "foo=bar", "intTag=99", "otherTag=")) shouldBe 99
        }

        test("Returns null when it encounters non-conforming tag") {
            // Returns null despite a correctly formatted tag is present
            intTag.find(setOf("intTag=", "intTag=99")).shouldBeNull()
        }
    }

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
