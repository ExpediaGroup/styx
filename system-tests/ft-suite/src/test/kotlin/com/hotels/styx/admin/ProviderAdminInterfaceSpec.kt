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
package com.hotels.styx.admin

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8

class ProviderAdminInterfaceSpec : FeatureSpec() {

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
                      
                providers:
                  myMonitor:
                    type: HealthCheckMonitor
                    config:
                      objects: aaa
                      path: /healthCheck/x
                      timeoutMillis: 250
                      intervalMillis: 500
                      healthyThreshold: 3
                      unhealthyThreshold: 2

                  mySecondMonitor:
                    type: HealthCheckMonitor
                    config:
                      objects: bbb
                      path: /healthCheck/y
                      timeoutMillis: 250
                      intervalMillis: 500
                      healthyThreshold: 3
                      unhealthyThreshold: 2

                httpPipeline:
                  type: StaticResponseHandler
                  config:
                    status: 200
                """.trimIndent()
            )

    init {
        styxServer.restart()

        feature("Provider admin interface endpoints") {
            scenario("Exposes endpoints for each provider") {
                styxServer.adminRequest("/admin/providers/myMonitor/status")
                        .bodyAs(UTF_8)
                        .shouldBe("""{ name: "HealthCheckMonitoringService" status: "RUNNING" }""")

                styxServer.adminRequest("/admin/providers/mySecondMonitor/status")
                        .bodyAs(UTF_8)
                        .shouldBe("""{ name: "HealthCheckMonitoringService" status: "RUNNING" }""")
            }
        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    fun StyxServerProvider.adminRequest(endpoint: String): HttpResponse = client
            .send(get(endpoint)
                    .header(HOST, this().adminHostHeader())
                    .build())
            .wait()

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
