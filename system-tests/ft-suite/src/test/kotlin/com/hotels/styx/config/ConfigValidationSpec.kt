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
package com.hotels.styx.config

import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.config.schema.SchemaValidationException
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class ConfigValidationSpec : StringSpec() {

    init {
        "Config is validated on startup" {
            val e = shouldThrow<SchemaValidationException> {
                StyxServer(StyxServerComponents.Builder()
                        .styxConfig(StyxConfig.fromYaml(yamlText))
                        .build())
            }

            e.message shouldBe("Missing a mandatory field 'proxy'")
        }
    }

    val yamlText = """
        wrong: foo
      """.trimIndent()
}
