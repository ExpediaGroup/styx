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
package com.hotels.styx.api.extension

import com.hotels.styx.api.extension.service.Http2ConnectionPoolSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Http2ConnectionPoolSettingsTest : StringSpec({
    "setsConfigurationValues" {
        val config = Http2ConnectionPoolSettings(10, 5, 10, 20)

        config.maxConnections shouldBe 10
        config.minConnections shouldBe 5
        config.maxStreamsPerConnection shouldBe 10
        config.maxPendingStreamsPerHost shouldBe 20
    }
})
