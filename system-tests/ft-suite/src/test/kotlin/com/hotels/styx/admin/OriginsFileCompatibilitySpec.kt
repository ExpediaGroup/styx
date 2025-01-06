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
package com.hotels.styx.admin

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.hotels.styx.NettyExecutor
import com.hotels.styx.admin.handlers.ServiceProviderHandler
import com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.routingObject
import com.hotels.styx.support.wait
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.time.Duration.Companion.seconds

class OriginsFileCompatibilitySpec : FunSpec() {
    val tempDir = createTempDir(suffix = "-${this.javaClass.simpleName}")
    val originsFile = File(tempDir, "origins.yml")
    val LOGGER = LoggerFactory.getLogger(OriginsFileCompatibilitySpec::class.java)

    val styxServer = StyxServerProvider(
            defaultConfig = """
                ---
                providers:
                  originsFileLoader:
                    type: YamlFileConfigurationService
                    config:
                      originsFile: ${originsFile.absolutePath}
                      ingressObject: pathPrefixRouter
                      monitor: True
                      pollInterval: PT0.1S

                httpPipeline: pathPrefixRouter

                proxy:
                  connectors:
                    http:
                      port: 0

                admin:
                  connectors:
                    http:
                      port: 0

                request-logging:
                  inbound:
                    enabled: true
                  outbound:
                    enabled: true
                """.trimIndent(),
            defaultLoggingConfig = ResourcePaths.fixturesHome(
                    OriginsFileCompatibilitySpec::class.java,
                    "/conf/logback/logback.xml")
                    .toAbsolutePath())

    init {
        context("Styx server starts") {
            val originsFile = """
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
            """.trimIndent()
            writeOrigins(originsFile)
            styxServer.restart()

            test("It populates forwarding path from the origins yaml file") {
                LOGGER.info("Object database: " + dumpObjectDatabase())

                eventually(2.seconds) {
                    client.send(get("/1")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                            }
                }
            }

            test("The origins config file is returned from the admin service") {
                client.send(get("/admin/providers/originsFileLoader/configuration")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe originsFile
                        }
            }
        }

        context("Failing to load an initial configuration blocks Styx HTTP server from starting.") {
            writeOrigins("""
                    - id: appA
                    - this file has somehow corrupted
                      .. bl;ah blah"
                    """.trimIndent())

            styxServer.restartAsync()

            delay(1.seconds)

            styxServer().proxyHttpAddress() shouldBe null
            styxServer.stop()
        }

        context("Origins configuration changes") {
            writeOrigins("""
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
            """.trimIndent())
            styxServer.restart()

            test("A new origin is added") {
                writeOrigins("""
                    - id: appA
                      path: "/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                      - { id: "appA-02", host: "localhost:${mockServerA02.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/2")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-01"
                            }
                }

                eventually(2.seconds) {
                    client.send(get("/3")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-02"
                            }
                }
            }

            test("Origin is removed") {
                writeOrigins("""
                    - id: appA
                      path: "/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                    """.trimIndent())

                delay(1.seconds)

                (1..20).forEach {
                    client.send(get("/4")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-01"
                            }
                }
            }

            test("Load balancing group is added") {
                writeOrigins("""
                    - id: appA
                      path: "/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                    - id: appB
                      path: "/b/"
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/b/5")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appB-01"
                            }
                }

                client.send(get("/6")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "appA-01"
                        }
            }

            test("Path mapping changes") {
                writeOrigins("""
                    - id: appA
                      path: "/a/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                    - id: appB
                      path: "/"
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/7")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appB-01"
                            }
                }

                client.send(get("/a/8")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "appA-01"
                        }
            }

            test("Host proxy port number changes") {
                writeOrigins("""
                    - id: appA
                      path: "/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/9")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-01"
                            }
                }

                writeOrigins("""
                    - id: appA
                      path: "/"
                      origins:
                      - { id: "appA-01", host: "localhost:${mockServerA02.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/10")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-02"
                            }
                }
            }

            test("Rewrites") {
                writeOrigins("""
                    - id: appB
                      path: "/b/"
                      rewrites:
                        - urlPattern: "/b/(.*)"
                          replacement: "/rewritten/$1"
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    - id: appC
                      path: "/c/"
                      rewrites:
                        - urlPattern: /c/abc/(.*)
                          replacement: /rewritten/${'$'}1
                        - urlPattern: /c/def/(.*)
                          replacement: /rewritten2/${'$'}1
                      origins:
                      - { id: "appC-01", host: "localhost:${mockServerC01.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/b/hello")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appB-01"
                            }
                }

                client.send(get("/c/abc/hello")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "appC-01"
                        }
                client.send(get("/c/def/hello2")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "appC-01"
                        }

                mockServerB01.verify(getRequestedFor(urlEqualTo("/rewritten/hello")))
                mockServerC01.verify(getRequestedFor(urlEqualTo("/rewritten/hello")))
                mockServerC01.verify(getRequestedFor(urlEqualTo("/rewritten2/hello2")))
            }
        }

        context("Origins restriction cookie") {
            writeOrigins("""
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                  - { id: "appA-02", host: "localhost:${mockServerA02.port()}" }
                """.trimIndent())

            styxServer.restart(
                    configuration = """
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
                              monitor: True
                              pollInterval: PT0.1S

                        originRestrictionCookie: ABC

                        httpPipeline: originsFileLoader-router
                        """.trimIndent())

            test("Routes to origin indicated by origins restriction cookie") {
                // Poll for server to become available
                eventually(2.seconds) {
                    client.send(get("/13")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                            }
                }

                for (i in 1..10) {
                    client.send(get("/14")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .cookies(requestCookie("ABC", "appA.appA-02"))
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-02"
                            }
                }
            }
        }

        context("Admin interface") {

            val validOriginsFile = """
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
            """.trimIndent()

            test("Styx dashboard is disabled") {
                client.send(get("/")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldNotContain ("/admin/dashboard/")
                        }

                client.send(get("/admin/dashboard/index.html")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            // Admin index page (with links)
                            it.bodyAs(UTF_8) shouldContain """<li><a href='/admin/configuration?pretty'>Configuration</a></li>"""
                        }

                client.send(get("/admin/dashboard/data.json")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            // Admin index page (with links)
                            it.bodyAs(UTF_8) shouldContain """<li><a href='/admin/configuration?pretty'>Configuration</a></li>"""
                        }
            }

            test("Modified origins config is returned after update") {

                writeOrigins(validOriginsFile)

                eventually(2.seconds) {
                    client.send(get("/admin/providers/originsFileLoader/configuration")
                            .header(HOST, styxServer().adminHostHeader())
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe validOriginsFile
                            }
                }
            }

            test("Original origins config is returned after invalid update") {

                writeOrigins("""
                    - id: appA
                    - this file has somehow corrupted
                      .. bl;ah blah"
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/admin/providers/originsFileLoader/configuration")
                            .header(HOST, styxServer().adminHostHeader())
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe validOriginsFile
                            }
                }
            }
        }

        context("Creates a health checking service") {

            fun deserialiseHealthCheckMonitor(yaml: String) =
                    ServiceProviderHandler.yamlMapper().readValue(yaml, StyxObjectDefinition::class.java)

            fun extractHealthCheckMonitors(responseBody: String) =
                    responseBody.split("---\n")
                            .filter(String::isNotBlank)
                            .map(::deserialiseHealthCheckMonitor)
                            .filter { it.type() == "HealthCheckMonitor" }

            fun validateHealthCheckMonitor(monitor: StyxObjectDefinition) {
                monitor.name() shouldBe "appB-monitor"
                monitor.type() shouldBe "HealthCheckMonitor"
                monitor.tags().shouldContainAll("source=originsFileLoader", "target=appB")

                val config = monitor.config()

                config.get("objects").asText() shouldBe "appB"
                config.get("path").asText() shouldBe "/healthcheck.txt"
                config.get("timeoutMillis").asLong() shouldBe 2000L
                config.get("intervalMillis").asLong() shouldBe 200L
                config.get("healthyThreshod").asInt() shouldBe 2
                config.get("unhealthyThreshold").asInt() shouldBe 2
            }

            mockServerB01.stub(WireMock.get(WireMock.urlMatching("/healthcheck.txt")), WireMock.aResponse()
                    .withStatus(500)
                    .withBody("origin unhealthy"))

            test("Sends traffic to an unhealthy origin when health checks are disabled") {
                writeOrigins("""
                    # 1. Start with health checks off
                    - id: appB
                      path: "/"
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/15")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appB-01"
                                Thread.sleep(200)
                            }
                }
            }

            test("Stops sending traffic to an unhealthy origin when health checks are enabled") {
                writeOrigins("""
                    # 2. Turn on the health checks
                    - id: appB
                      path: "/"
                      healthCheck:
                          uri: "/healthcheck.txt"
                          intervalMillis: 200
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    """.trimIndent())

                delay(1.seconds)

                client.send(get("/16")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe BAD_GATEWAY
                            it.header("X-Styx-Origin-Id").get() shouldBe "appB"
                            it.bodyAs(UTF_8) shouldBe "Site temporarily unavailable."
                        }
            }

            test("Health checking service appears in the service providers list on the admin endpoint") {
                client.send(get("/admin/service/providers")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            val monitors = extractHealthCheckMonitors(it.bodyAs(UTF_8))
                            monitors shouldHaveSize 1
                            validateHealthCheckMonitor(monitors[0])
                        }
            }

            test("Health checking service returned from the admin endpoint") {
                client.send(get("/admin/service/provider/appB-monitor")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            val monitor = deserialiseHealthCheckMonitor(it.bodyAs(UTF_8))
                            validateHealthCheckMonitor(monitor)
                        }
            }

            test("Health checking service status returned from its admin endpoint") {
                client.send(get("/admin/providers/appB-monitor/status")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            it.header(CONTENT_TYPE).get().lowercase() shouldBe APPLICATION_JSON.toString().lowercase()
                            // TODO: This name should probably change.
                            it.bodyAs(UTF_8) shouldBe "{ name: \"HealthCheckMonitoringService-appB\" status: \"RUNNING\" }"
                        }
            }


            test("Starts sending traffic to an unhealthy origin again when health checks are disabled") {
                writeOrigins("""
                    # 3. Turn off the health checks again
                    - id: appB
                      path: "/"
                      origins:
                      - { id: "appB-01", host: "localhost:${mockServerB01.port()}" }
                    """.trimIndent())

                delay(1.seconds)

                eventually(3.seconds) {
                    client.send(get("/17")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appB-01"
                                Thread.sleep(200)
                            }
                }
            }

            test("Health checking service does not appear in the service providers list on the admin endpoint") {
                client.send(get("/admin/service/providers")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                            val monitors = extractHealthCheckMonitors(it.bodyAs(UTF_8))
                            monitors shouldHaveSize 0
                        }
            }

            test("No health checking service found on the admin endpoint") {
                client.send(get("/admin/service/provider/appB")
                        .header(HOST, styxServer().adminHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe NOT_FOUND
                        }
            }
        }

        context("Executors configuration") {
            writeOrigins("""
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
            """.trimIndent())

            test("Uses named executor") {
                styxServer.restart(
                        configuration = """
                        ---
                        executors:
                          test-executor:
                            type: NettyExecutor
                            config:
                              threads: 1
                              namePattern: MyTestExecutor

                        providers:
                          originsFileLoader:
                            type: YamlFileConfigurationService
                            config:
                              originsFile: ${originsFile.absolutePath}
                              ingressObject: pathPrefixRouter
                              monitor: True
                              pollInterval: PT0.1S
                              executor: test-executor

                        httpPipeline: pathPrefixRouter

                        proxy:
                          connectors:
                            http:
                              port: 0

                        admin:
                          connectors:
                            http:
                              port: 0
                    """.trimIndent())


                eventually(2.seconds) {
                    client.send(get("/foo")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                            }
                }

                styxServer().routingObject("appA.appA-01", debug = true).get()
                        .shouldContain("executor: \"test-executor\"")
            }

            test("Uses default executor, when named executor is not provided") {
                styxServer.restart(
                        configuration = """
                        ---
                        providers:
                          originsFileLoader:
                            type: YamlFileConfigurationService
                            config:
                              originsFile: ${originsFile.absolutePath}
                              ingressObject: pathPrefixRouter
                              monitor: True
                              pollInterval: PT0.1S

                        httpPipeline: pathPrefixRouter

                        proxy:
                          connectors:
                            http:
                              port: 0

                        admin:
                          connectors:
                            http:
                              port: 0
                    """.trimIndent())

                eventually(2.seconds) {
                    client.send(get("/foo/2")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait()!!
                            .let {
                                it.status() shouldBe OK
                            }
                }

                styxServer().routingObject("appA.appA-01", debug = true).get()
                        .shouldContain("executor: \"Styx-Client-Global-Worker\"")
            }
        }



        context("Error scenarios") {
            val originsFile = """
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-01", host: "localhost:${mockServerA01.port()}" }
                """.trimIndent()
            writeOrigins(originsFile)
            styxServer.restart()

            test("Keeps the original configuration when a one has problems") {
                eventually(2.seconds) {
                    client.send(get("/18")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                            }
                }

                // Reload incorrect configuration
                writeOrigins("""
                    - id: appA
                    - this file has somehow corrupted
                      .. bl;ah blah"
                    """.trimIndent())

                delay(1.seconds)

                client.send(get("/19")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait().let {
                            it!!.status() shouldBe OK
                        }
            }

            test("Reloads a new configuration after error") {
                val newOriginsFile = """
                - id: appA
                  path: "/"
                  origins:
                  - { id: "appA-02", host: "localhost:${mockServerA02.port()}" }
                """.trimIndent()
                writeOrigins(newOriginsFile)

                eventually(2.seconds) {
                    client.send(get("/20")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait().let {
                                it!!.status() shouldBe OK
                                it.bodyAs(UTF_8) shouldBe "appA-02"
                            }
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

    fun dumpObjectDatabase() = client.send(get("/admin/routing/objects")
            .header(HOST, styxServer().adminHostHeader())
            .build())
            .wait()
            .bodyAs(UTF_8)!!

    val mockServerA01 = MockOriginServer.create("appA", "appA-01", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("X-Origin", "Server-A01-server")
                    .withBody("appA-01"))

    val mockServerA02 = MockOriginServer.create("appA", "appA-02", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("X-Origin", "Server-A02-server")
                    .withBody("appA-02"))

    val mockServerB01 = MockOriginServer.create("appB", "appB-01", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("X-Origin", "Server-B01-server")
                    .withBody("appB-01"))

    val mockServerC01 = MockOriginServer.create("appC", "appC-01", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("X-Origin", "Server-C01-server")
                    .withBody("appC-01"))

    val mockTlsv12Server = MockOriginServer.create("appTls", "appTls-01", 0,
            HttpsConnectorConfig.Builder()
                    .port(0)
                    .sslProvider("JDK")
                    .protocols("TLSv1.2")
                    .build())
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("X-Origin", "TlsV2-server")
                    .withBody("appTls-01"))

    override suspend fun afterSpec(spec: Spec) {
        styxServer.stop()
        tempDir.deleteRecursively()

        mockServerA01.stop()
        mockServerA02.stop()
        mockServerB01.stop()
        mockServerC01.stop()
        mockTlsv12Server.stop()
    }
}

private val client: StyxHttpClient = StyxHttpClient.Builder()
        .executor(NettyExecutor.create("styx-client", 0))
        .build()
