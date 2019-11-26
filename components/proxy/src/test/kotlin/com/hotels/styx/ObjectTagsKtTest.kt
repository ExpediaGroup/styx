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
                "lbGroup=abc".match(lbGroupTag).shouldBe("abc")
            }
        }
        `when`("tag doesn't match") {
            then("returns null") {
                "abc".match(lbGroupTag).shouldBeNull()
//                "lbGroup=".match(lbGroupTag).shouldBeNull()
                "".match(lbGroupTag).shouldBeNull()
            }
        }
    }

    given("a healthCheck tag factory method") {
        `when`("the label is not blank and the count is >= 0") {
            then("a tag string is returned") {
                healthCheckTag("probesOK" to 1) shouldBe "healthCheck=on;probesOK:1"
                healthCheckTag("probesNOK" to 7) shouldBe "healthCheck=on;probesNOK:7"
                healthCheckTag("on" to 0) shouldBe "healthCheck=on"
            }
        }
        `when`("the label is blank") {
            then("null is returned") {
                // Will now throw:
                healthCheckTag(Pair("", 7)) shouldBe null
            }
        }
        `when`("the label is not blank and the count is <= 0") {
            then("null is returned") {
                healthCheckTag(Pair("passing", 0)) shouldBe "healthCheck=on"
                healthCheckTag(Pair("failing", -1)) shouldBe null
            }
        }
    }

    given("a healthCheck tag decoding method") {
        `when`("a valid tag is decoded") {
            then("decoded data is returned") {
                "healthCheck=on;probesOK:1".match(healthCheckTag) shouldBe Pair("probesOK", 1)
                "healthCheck=on;probesNOK:2".match(healthCheckTag) shouldBe Pair("probesNOK", 2)
                "healthCheck=on".match(healthCheckTag) shouldBe Pair("on", 0)
            }
        }
        `when`("an invalid tag is decoded") {
            then("null is returned") {
                "healthCheck=on;probesOK:-1".match(healthCheckTag) shouldBe null
                "healthCheck=".match(healthCheckTag) shouldBe null
                "healthCheck=on;probesOK".match(healthCheckTag) shouldBe null
                "healthCheck=on;probesOK:".match(healthCheckTag) shouldBe null
                "healthCheck=:1".match(healthCheckTag) shouldBe null
                "healthCheck".match(healthCheckTag) shouldBe null
                "healthCheckXX=probesOK:0".match(healthCheckTag) shouldBe null
                "XXhealthCheck=probesOK:0".match(healthCheckTag) shouldBe null
                "healthCheck=probesOK:0X".match(healthCheckTag) shouldBe null
                "".match(healthCheckTag) shouldBe null
            }
        }
    }
})
