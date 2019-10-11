package com.hotels.styx.routing

import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.specs.StringSpec
import io.mockk.mockk

class RoutingObjectRecordTest : StringSpec({
    "Creates with timestamp" {
        val createdTag= RoutingObjectRecord.create("A", setOf("b"), mockk(), mockk())
                .tags
                .filter { it.startsWith("created") }
                .first()

        createdTag.shouldContain("created:20[0-9]{2}-[0-9]{1,2}-[0-9]{1,2}T[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}.[0-9]{3}".toRegex())
    }
})
