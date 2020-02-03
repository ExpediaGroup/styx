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
package com.hotels.styx.routing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.metrics
import com.hotels.styx.support.newRoutingObject
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.proxyHttpsHostHeader
import com.hotels.styx.support.removeRoutingObject
import com.hotels.styx.support.threadCount
import com.hotels.styx.support.wait
import io.kotlintest.IsolationMode
import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.TestStatus
import io.kotlintest.eventually
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.beLessThan
import io.kotlintest.matchers.numerics.shouldBeInRange
import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.matchers.withClue
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.system.measureTimeMillis

class HostProxySpec : FeatureSpec() {
    val LOGGER = LoggerFactory.getLogger("HostProxySpec")

    // Enforce one instance for the test spec.
    // Run the tests sequentially:
    override fun isolationMode(): IsolationMode = IsolationMode.SingleInstance

    override fun beforeSpec(spec: Spec) {
        testServer.restart()
        styxServer.restart()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        mockServer.stop()
        testServer.stop()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)

        when (result.status) {
            TestStatus.Error -> {
                LOGGER.info("HostProxySpec: Error: Styx server : {}", styxServer().metrics())
                LOGGER.info("HostProxySpec: Error: Test server : {}", testServer().metrics())
            }
            TestStatus.Failure -> {
                LOGGER.info("HostProxySpec: Failure: Styx server : {}", styxServer().metrics())
                LOGGER.info("HostProxySpec: Failure: Test server : {}", testServer().metrics())
            }
            else -> { }
        }
    }

    init {
        // There are other tests that set the JVM system property io.netty.eventLoopThreads=16,
        // thus potentially affecting and breaking this test.
        feature("!Executor thread pool") {
            scenario("Runs on StyxHttpClient global thread pool") {
                testServer.restart()
                styxServer.restart()

                for (i in 1..4) {
                    styxServer().newRoutingObject("hostProxy", """
                           type: HostProxy
                           config:
                             host: localhost:${mockServer.port()}
                        """.trimIndent()) shouldBe CREATED

                    client.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait()!!
                            .status() shouldBe OK

                    styxServer().removeRoutingObject("hostProxy")
                }

                withClue("Thread count") {
                    threadCount("Styx-Client-Global") shouldBe 2
                }
            }
        }

        feature("Proxying requests") {
            scenario("Response Timeout") {
                styxServer().newRoutingObject("hostProxy", """
                           type: HostProxy
                           config:
                             host: localhost:${mockServer.port()}
                             responseTimeoutMillis: 600
                        """.trimIndent()) shouldBe CREATED

                measureTimeMillis {
                    client.send(get("/slow/n")
                            .header(HOST, styxServer().proxyHttpHostHeader())
                            .build())
                            .wait()!!
                            .status() shouldBe GATEWAY_TIMEOUT
                }.let { delay ->
                    delay shouldBe (beGreaterThan(600) and beLessThan(1000))
                }
            }

            scenario("Applies TLS settings") {
                styxServer().newRoutingObject("hostProxy", """
                        type: HostProxy
                        config:
                          host: ${testServer.get().proxyHttpsHostHeader()}
                          tlsSettings:
                            trustAllCerts: true
                            sslProvider: JDK
                    """.trimIndent())

                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()
                        .let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "Hello - HTTPS"
                        }
            }

            scenario("Applies max header size settings") {
                val maxHeaderSize = 20
                styxServer().newRoutingObject("hostProxy", """
                           type: HostProxy
                           config:
                             host: ${testServer().proxyHttpHostHeader()}
                             maxHeaderSize: $maxHeaderSize
                           """.trimIndent()) shouldBe CREATED

                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .status() shouldBe BAD_GATEWAY
            }

        }


        feature("Connection pooling") {
            scenario("Pools connections") {
                testServer.restart()
                styxServer.restart()

                styxServer().newRoutingObject("hostProxy", """
                           type: HostProxy
                           config:
                             host: localhost:${testServer().proxyHttpAddress().port}
                             connectionPool:
                               maxConnectionsPerHost: 2
                               maxPendingConnectionsPerHost: 10
                               pendingConnectionTimeoutMillis: 15000
                           """.trimIndent()) shouldBe CREATED

                val requestFutures = (1..10)
                        .map { "/$it" }
                        .map { it to client.send(get(it).header(HOST, styxServer().proxyHttpHostHeader()).build()) }

                requestFutures
                        .forEach { (url, future) ->
                            val clientResponse = future.wait()
                            LOGGER.info("Response: $url -> ${clientResponse!!.status()}")
                            clientResponse.status() shouldBe OK
                            clientResponse.bodyAs(UTF_8) shouldBe "Hello - HTTP"
                        }

                withClue("Origin connections.total-connections") {
                    testServer().metrics().let {
                        (it["connections.total-connections"]!!.get("count") as Int) shouldBeInRange 1..2
                    }
                }

                withClue("Styx Server routing.objects.hostProxy.connectionspool.connection-attempts") {
                    styxServer().metrics().let {
                        (it["routing.objects.hostProxy.connectionspool.connection-attempts"]!!.get("value") as Int) shouldBeInRange 1..2
                    }
                }
            }

            scenario("Applies connection expiration settings") {
                val connectinExpiryInSeconds = 1
                testServer.restart()
                styxServer.restart()

                styxServer().newRoutingObject("hostProxy", """
                           type: HostProxy
                           config:
                             host: ${testServer().proxyHttpHostHeader()}
                             connectionPool:
                               maxConnectionsPerHost: 2
                               maxPendingConnectionsPerHost: 10
                               connectionExpirationSeconds: $connectinExpiryInSeconds
                           """.trimIndent()) shouldBe CREATED

                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .status() shouldBe OK

                eventually(1.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["routing.objects.hostProxy.connectionspool.available-connections"]!!.get("value") shouldBe 1
                        it["routing.objects.hostProxy.connectionspool.connections-closed"]!!.get("value") shouldBe 0
                    }
                }

                // Wait for connection to expiry
                Thread.sleep(connectinExpiryInSeconds*1000L)

                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .status() shouldBe OK

                eventually(1.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["routing.objects.hostProxy.connectionspool.available-connections"]!!.get("value") shouldBe 1
                        it["routing.objects.hostProxy.connectionspool.connections-terminated"]!!.get("value") shouldBe 1
                    }
                }
            }
        }


        feature("Metrics collecting") {

            scenario("Restart servers and configure hostProxy object") {
                testServer.restart()
                styxServer.restart()

                styxServer().newRoutingObject("hostProxy", """
                                type: HostProxy
                                config:
                                  host: localhost:${mockServer.port()}
                                """.trimIndent()) shouldBe CREATED
            }

            scenario("... and send request") {
                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()
                        .let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "mock-server-01"
                        }
            }

            scenario("... and provides connection pool metrics") {
                styxServer().metrics().let {
                    it["routing.objects.hostProxy.connectionspool.connection-attempts"]!!.get("value") shouldBe 1
                }
            }

            scenario("... and provides origin and application metrics") {
                styxServer().metrics().let {
                    it["routing.objects.hostProxy.requests.response.status.200"]!!.get("count") shouldBe 1
                    it["routing.objects.hostProxy.connectionspool.connection-attempts"]!!.get("value") shouldBe 1
                }
            }

            scenario("... and unregisters connection pool metrics") {
                styxServer().removeRoutingObject("hostProxy")

                eventually(2.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["routing.objects.hostproxy.connectionspool.connection-attempts"].shouldBeNull()
                    }
                }
            }

            // Continues from previous test
            scenario("!Unregisters origin/application metrics") {
                // TODO: Not supported yet. An existing issue within styx.

                eventually(2.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["routing.objects.hostProxy.requests.response.status.200"].shouldBeNull()
                        it["routing.objects.hostProxy.localhost:${mockServer.port()}.requests.response.status.200"].shouldBeNull()
                    }
                }
            }
        }


        feature("Metrics collecting with metric prefix") {

            scenario("Restart servers and configure hostProxy object with metric prefix") {
                testServer.restart()
                styxServer.restart()

                styxServer().newRoutingObject("hostProxy", """
                                type: HostProxy
                                config:
                                  host: localhost:${mockServer.port()}
                                  metricPrefix: origins.myApp
                                """.trimIndent()) shouldBe CREATED
            }

            scenario("... and send request") {
                client.send(get("/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()
                        .let {
                            it!!.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "mock-server-01"
                        }
            }

            scenario("... and provides connection pool metrics with metric prefix") {
                styxServer().metrics().let {
                    it["origins.myApp.hostProxy.connectionspool.connection-attempts"]!!.get("value") shouldBe 1
                }
            }

            scenario("... and provides origin/application metrics with metric prefix") {
                styxServer().metrics().let {
                    it["origins.myApp.hostProxy.requests.response.status.200"]!!.get("count") shouldBe 1
                    it["origins.myApp.requests.response.status.200"]!!.get("count") shouldBe 1
                }
            }

            scenario("... and unregisters prefixed connection pool metrics") {
                styxServer().removeRoutingObject("hostProxy")

                eventually(2.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["origins.myApp.localhost:${mockServer.port()}.connectionspool.connection-attempts"].shouldBeNull()
                    }
                }
            }

            // Continues from previous test
            scenario("!Unregisters prefixed origin/application metrics") {
                // TODO: Not supported yet. An existing issue within styx.
                eventually(2.seconds, AssertionError::class.java) {
                    styxServer().metrics().let {
                        it["origins.myApp.localhost:${mockServer.port()}.requests.response.status.200"].shouldBeNull()
                        it["origins.myApp.requests.response.status.200"].shouldBeNull()
                    }
                }
            }
        }
    }

    private val styxServer = StyxServerProvider(
            defaultConfig="""
                proxy:
                  connectors:
                    http:
                      port: 0
                  clientWorkerThreadsCount: 3

                admin:
                  connectors:
                    http:
                      port: 0
                      
                services:
                  factories: {}                                     

                httpPipeline: hostProxy
                """.trimIndent()
            )

    private val testServer = StyxServerProvider("""
                                proxy:
                                  connectors:
                                    http:
                                      port: 0
                                    https:
                                      port: 0

                                admin:
                                  connectors:
                                    http:
                                      port: 0
                                      
                                services:
                                  factories: {}

                                httpPipeline:
                                  type: ConditionRouter
                                  config:
                                    routes:
                                      - condition: protocol() == "https"
                                        destination:
                                          type: StaticResponseHandler
                                          config:
                                            status: 200
                                            content: "Hello - HTTPS"
                                    fallback:
                                      type: StaticResponseHandler
                                      config:
                                        status: 200
                                        content: "Hello - HTTP"
                              """.trimIndent())

    val client: StyxHttpClient = System.setProperty("io.netty.eventLoopThreads", "2").let {StyxHttpClient.Builder().build()}

    val mockServer = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(urlMatching("/.*")), aResponse()
                    .withStatus(200)
                    .withBody("mock-server-01")
                    .withHeader("HEADER", "RANDOMLONGVALUETOVERIFYMAXHEADERSIZE")
            )

            .stub(WireMock.get(urlMatching("/slow/.*")), aResponse()
                    .withStatus(200)
                    .withFixedDelay(1500)
                    .withBody("mock-server-01 slow"))
}
