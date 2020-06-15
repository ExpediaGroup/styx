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
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.metrics
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.shouldBeInRange
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.FunSpec
import io.micrometer.core.instrument.Gauge
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

class MetricsSpec : FunSpec() {
    val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    val originsFile = File(tempDir, "origins.yml")
    val LOGGER = LoggerFactory.getLogger(OriginsFileCompatibilitySpec::class.java)

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
                  originsFileLoader:
                    type: YamlFileConfigurationService
                    config:
                      originsFile: ${originsFile.absolutePath}
                      ingressObject: pathPrefixRouter
                      monitor: True
                      pollInterval: PT0.1S 

                httpPipeline: pathPrefixRouter
                """.trimIndent(),
            defaultLoggingConfig = ResourcePaths.fixturesHome(
                    OriginsFileCompatibilitySpec::class.java,
                    "/conf/logback/logback.xml")
                    .toAbsolutePath())

    init {
        context("Origin metrics in backwards compatibility mode") {
            writeOrigins("""
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" } 
            """.trimIndent())
            styxServer.restart()

            test("Connection pool metrics path") {
                eventually(2.seconds, AssertionError::class.java) {
                    testClient.send(get("/")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-01"
                            }
                }

                eventually(1.seconds, AssertionError::class.java) {
                    styxServer.meterRegistry().find("connectionspool.available-connections")
                            .tags("appid", "origins", "originid", "appA.appA-01")
                            .gauge() shouldNotBe null
                }
            }

            test("time-to-first-byte metrics are reported") {
                styxServer().metrics().let {
                    (it["origins.appA.appA-01.requests.time-to-first-byte"]!!["count"] as Int) shouldBeGreaterThan 0
                    (it["origins.appA.appA-01.requests.time-to-first-byte"]!!["mean"] as Double) shouldBeGreaterThan 0.0
                    (it["origins.requests.time-to-first-byte"]!!["count"] as Int) shouldBeGreaterThan 0
                    (it["origins.requests.time-to-first-byte"]!!["mean"] as Double) shouldBeGreaterThan 0.0
                }
            }
        }
    }

    internal fun writeOrigins(text: String, debug: Boolean = true) {
        originsFile.writeText(text)
        if (debug) {
            LOGGER.info("new origins file: \n${originsFile.readText()}")
        }
    }

    val mockServerA01 = MockOriginServer.create("appA", "appA-01", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("appA-01"))

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        tempDir.deleteRecursively()
        mockServerA01.stop()
    }
}
