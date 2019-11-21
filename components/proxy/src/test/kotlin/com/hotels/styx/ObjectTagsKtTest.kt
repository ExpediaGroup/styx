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

import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec

class ObjectTagsKtTest : BehaviorSpec({
    given("An lbGroup tag matcher") {
        `when`("tag matches") {
            then("returns tag value") {
                lbGroupTagValue("lbGroup=abc").shouldBe("abc")
            }
        }
        `when`("tag doesn't match") {
            then("returns null") {
                lbGroupTagValue("abc").shouldBeNull()
                lbGroupTagValue("lbGroup=").shouldBeNull()
                lbGroupTagValue("").shouldBeNull()
            }
        }
    }

    given("a healthcheck tag factory method") {
        `when`("the label is not blank and the count is > 0") {
            then("a tag string is returned") {
                healthcheckTag("passing" to 1) shouldBe "healthcheck=passing:1"
                healthcheckTag("failing" to 7) shouldBe "healthcheck=failing:7"
            }
        }
        `when`("the label is blank") {
            then("null is returned") {
                healthcheckTag(Pair("", 7)) shouldBe null
            }
        }
        `when`("the label is not blank and the count is <= 0") {
            then("null is returned") {
                healthcheckTag(Pair("passing", 0)) shouldBe null
                healthcheckTag(Pair("failing", -1)) shouldBe null
            }
        }
        `when`("the factory data is null") {
            then("null is returned") {
                healthcheckTag(null) shouldBe null
            }
        }
    }

    given("a healthcheck tag decoding method") {
        `when`("a valid tag is decoded") {
            then("decoded data is returned") {
                healthcheckTagValue("healthcheck=passing:0") shouldBe Pair("passing", 0)
                healthcheckTagValue("healthcheck=failing:2") shouldBe Pair("failing", 2)
            }
        }
        `when`("an invalid tag is decoded") {
            then("null is returned") {
                healthcheckTagValue("healthcheck=passing:-1") shouldBe null
                healthcheckTagValue("healthcheck=") shouldBe null
                healthcheckTagValue("healthcheck=passing") shouldBe null
                healthcheckTagValue("healthcheck=passing:") shouldBe null
                healthcheckTagValue("healthcheck=:1") shouldBe null
                healthcheckTagValue("healthcheck") shouldBe null
                healthcheckTagValue("healthcheckXX=passing:0") shouldBe null
                healthcheckTagValue("XXhealthcheck=passing:0") shouldBe null
                healthcheckTagValue("healthcheck=passing:0X") shouldBe null
                healthcheckTagValue("") shouldBe null
            }
        }
    }
})
