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
package com.hotels.styx.config

import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.config.schema.SchemaValidationException
import com.hotels.styx.startup.StyxServerComponents
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ConfigValidationSpec : StringSpec({
        "Config is validated on startup" {
            val e = shouldThrow<SchemaValidationException> {
                StyxServer(StyxServerComponents.Builder()
                        .styxConfig(StyxConfig.fromYaml("""
                            wrong: foo
                          """.trimIndent()))
                        .build())
            }

            e.message shouldBe("Missing a mandatory field 'admin'")
        }
    })
