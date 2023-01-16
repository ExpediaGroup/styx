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

import com.hotels.styx.api.extension.service.TcpKeepAliveSettings
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class TcpKeepAliveSettingsTest : StringSpec({
    "setsConfigurationValues" {
        val config = TcpKeepAliveSettings(100, 30, 3)

        config.keepAliveIdleTimeSeconds() shouldBe 100
        config.keepAliveIntervalSeconds() shouldBe 30
        config.keepAliveRetryCount() shouldBe 3
    }

    "shouldBuildFromOtherTcpKeepAliveSettings" {
        val config = TcpKeepAliveSettings(120, 60, 8)
        val newConfig = TcpKeepAliveSettings.Builder(config)
            .keepAliveIdleTimeSeconds(20)
            .build()

        newConfig.keepAliveIdleTimeSeconds() shouldBe 20
        newConfig.keepAliveIntervalSeconds() shouldBe 60
        newConfig.keepAliveRetryCount() shouldBe 8
    }
})
