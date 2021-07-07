/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.admin

import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminRequest
import io.kotlintest.Spec
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8

class AdminInterfaceSpec : FeatureSpec() {
    val styxServer = StyxServerProvider(
            defaultConfig = """
                ---
                proxy:
                  connectors:
                    http:
                      port: 0
        
                admin:
                  connectors:
                    http:
                      port: 0

                httpPipeline:
                  type: StaticResponseHandler
                  config:
                    status: 200
                """.trimIndent()
    )

    init {
        feature("Styx Server Admin Interface Index") {
            styxServer.restart()

            scenario("Uptime endpoint link") {
                styxServer.adminRequest("/admin", debug = true)
                        .bodyAs(UTF_8)
                        .shouldContain("<a href='/admin/uptime'>uptime</a>".toRegex())
            }
        }
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
