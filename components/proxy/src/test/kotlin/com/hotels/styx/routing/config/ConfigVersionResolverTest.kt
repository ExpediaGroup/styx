/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.routing.config;

import com.hotels.styx.StyxConfig
import com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V2
import com.hotels.styx.routing.config.ConfigVersionResolver.configVersion
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec

class ConfigVersionResolverTest : StringSpec({

    "Returns v2 when 'httpPipeline' is present" {
        val config = StyxConfig.fromYaml("""
            httpPipeline:
              pipeline:
                - plugA
                - plugB
              handler:
                name: MyHandler
                type: ProxyToBackend
                config:
                  bar: 1
        """.trimIndent(), false)

        configVersion(config).shouldBe(ROUTING_CONFIG_V2)
    }

})
