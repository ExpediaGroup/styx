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
package com.hotels.styx.routing

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

private const val CREATED_STRING = "created:"
private const val TIMESTAMP_START_POSITION = CREATED_STRING.length

class RoutingObjectRecordTest : StringSpec({
    "Creates with timestamp" {
        val createdTag = RoutingObjectRecord.create("A", setOf("b"), mockk(), mockk())
                .tags
                .filter { it.startsWith(CREATED_STRING) }
                .map { it.substring(TIMESTAMP_START_POSITION) }
                .first()

        LocalDateTime.parse(createdTag, ISO_DATE_TIME).format(ISO_DATE_TIME).shouldBe(createdTag);

    }
})
