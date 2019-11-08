package com.hotels.styx

import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec

class ObjectTagsKtTest : BehaviorSpec({
    given("An lbGroup tag matcher") {
        `when`("tag matches") {
            then("returns tag value") {
                matchLbGroupTag("lbGroup=abc").shouldBe("abc")
            }
        }
        `when`("tag doesn't match") {
            then("returns null") {
                matchLbGroupTag("abc").shouldBeNull()
                matchLbGroupTag("lbGroup=").shouldBeNull()
                matchLbGroupTag("").shouldBeNull()
            }
        }
    }
})
