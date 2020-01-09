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
package com.hotels.styx.admin

import com.github.tomakehurst.wiremock.client.WireMock
import com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldNotContain
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

class ProviderAdminInterfaceSpec : FeatureSpec() {
    private val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    private val originsFile = File(tempDir, "origins.yml")

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

                  originsFileLoader:
                    type: YamlFileConfigurationService
                    config:
                      originsFile: ${originsFile.absolutePath}
                      ingressObject: pathPrefixRouter
                      monitor: True
                      pollInterval: PT0.1S 

                httpPipeline:
                  type: StaticResponseHandler
                  config:
                    status: 200
                """.trimIndent()
            )

    private val mockServer = MockOriginServer.create("mock", "mock-01", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("appA-01"))

    init {
        writeOriginsFile(Pair("appA", false))
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

            scenario ("Provider list page contains links for each provider endpoint") {
                val body = styxServer.adminRequest("/admin/providers")
                        .bodyAs(UTF_8)
                body shouldContain "/admin/providers/myMonitor/status"
                body shouldContain "/admin/providers/mySecondMonitor/status"
            }
        }

        feature("Endpoints for dynamically added Styx services are available in the Admin interface") {

            scenario("Additional endpoints are listed on the Providers admin interface") {

                writeOriginsFile(Pair("appA", false), Pair("appB", true))

                eventually(1.seconds, AssertionError::class.java) {
                    val body = styxServer.adminRequest("/admin/providers")
                            .bodyAs(UTF_8)
                    body shouldContain "/admin/providers/myMonitor/status"
                    body shouldContain "/admin/providers/mySecondMonitor/status"
                    body shouldNotContain "/admin/providers/appA-monitor/status"
                    body shouldContain "/admin/providers/appB-monitor/status"
                }
            }

            scenario("The new admin endpoint returns status information without server restart") {
                val responseA = styxServer.adminRequest("/admin/providers/appA-monitor/status")
                responseA.status() shouldBe NOT_FOUND
                val responseB = styxServer.adminRequest("/admin/providers/appB-monitor/status")
                responseB.status() shouldBe OK
                responseB.header(CONTENT_TYPE).get().toLowerCase() shouldBe APPLICATION_JSON.toString().toLowerCase()
                responseB.bodyAs(UTF_8) shouldBe "{ name: \"HealthCheckMonitoringService\" status: \"RUNNING\" }"
            }

            scenario("Endpoints for dynamically removed Styx services are not listed in the Admin interface") {

                writeOriginsFile(Pair("appA", false))

                eventually(1.seconds, AssertionError::class.java) {
                    val body = styxServer.adminRequest("/admin/providers")
                            .bodyAs(UTF_8)
                    body shouldContain "/admin/providers/myMonitor/status"
                    body shouldContain "/admin/providers/mySecondMonitor/status"
                    body shouldNotContain "/admin/providers/appA-monitor/status"
                    body shouldNotContain "/admin/providers/appB-monitor/status"
                }

            }

            scenario("The removed endpoint returns HTTP NOT_FOUND status") {
                val responseA = styxServer.adminRequest("/admin/providers/appA-monitor/status")
                responseA.status() shouldBe NOT_FOUND
                val responseB = styxServer.adminRequest("/admin/providers/appB-monitor/status")
                responseB.status() shouldBe NOT_FOUND
            }
        }

    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    fun StyxServerProvider.adminRequest(endpoint: String): HttpResponse = client
            .send(get(endpoint)
                    .header(HOST, this().adminHostHeader())
                    .build())
            .wait()

    private fun writeOriginsFile(vararg origins: Pair<String, Boolean>) {
        origins.joinToString (separator = "\n") {
            val part1 = """
                |- id: ${it.first}
                |  path: "/"
            """.trimMargin()
            val part2 = """
                |  healthCheck:
                |    uri: "http://www/check/me"
            """.trimMargin()
            val part3 = """
                |  origins:
                |  - { id: "${it.first}-origin", host: "localhost:${mockServer.port()}" }
            """.trimMargin()
            if (it.second) "$part1\n$part2\n$part3" else "$part1\n$part3"
        }.also { originsFile.writeText(it) }
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }
}
