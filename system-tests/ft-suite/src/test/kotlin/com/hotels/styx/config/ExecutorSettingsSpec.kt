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
package com.hotels.styx.config

import com.github.tomakehurst.wiremock.client.WireMock
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.serverPort
import com.hotels.styx.support.threadCount
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8

class ExecutorSettingsSpec : FeatureSpec() {

    val mockServer = MockOriginServer.create("", "", 0, HttpConnectorConfig(0))
            .start()
            .stub(WireMock.get(WireMock.urlMatching("/.*")), WireMock.aResponse()
                    .withStatus(200)
                    .withBody("mock-server-01"))

    init {
        feature("Executors configuration") {

            scenario("Applies client executor to HostProxy objects") {
                styxServer.stop()
                styxServer.restart(configuration = """
                    executors:
                      forHostProxy:
                        type: NettyExecutor
                        config:
                          threads: 1
                          namePattern: host-proxy
            
                    httpPipeline:
                      type: HostProxy
                      config:
                        host: "localhost:${mockServer.port()}"
                        executor: forHostProxy
            
                    proxy:
                      connectors:
                        http:
                          port: 0
            
                    admin:
                      connectors:
                        http:
                          port: 0
                  """.trimIndent())

                client.send(get("/a/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "mock-server-01"
                        }

                threadCount("host-proxy") shouldBe 1
            }

            scenario("HostProxy uses default configuration when `executors` are not specified") {
                // It is impossible to verify, externally, on what thread HostProxy runs.
                // The best we can do is to make sure it still works.
                styxServer.restart(configuration = """
                    httpPipeline:
                      type: HostProxy
                      config:
                        host: "localhost:${mockServer.port()}"
            
                    proxy:
                      connectors:
                        http:
                          port: 0
            
                    admin:
                      connectors:
                        http:
                          port: 0
                  """.trimIndent())

                client.send(get("/a/")
                        .header(HOST, styxServer().proxyHttpHostHeader())
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "mock-server-01"
                        }
            }

            scenario("Applies server executor to StyxHttpServer objects") {
                styxServer.restart(configuration = """
                    executors:
                      boss-executor:
                        type: NettyExecutor
                        config:
                          threads: 1
                          namePattern: http-boss-executor
                      worker-executor:
                        type: NettyExecutor
                        config:
                          threads: 1
                          namePattern: http-worker-executor
            
                    routingObjects:
                      static-response:
                          type: StaticResponseHandler
                          config:
                            status: 200
                            content: "Hello, from styx server!"
            
                    servers: 
                      http:
                        type: HttpServer
                        config:
                          port: 0
                          handler: static-response
                          bossExecutor: boss-executor
                          workerExecutor: worker-executor
            
                    admin:
                      connectors:
                        http:
                          port: 0
                  """.trimIndent())

                val httpPort = styxServer().serverPort("http")

                client.send(get("/b/")
                        .header(HOST, "localhost:$httpPort")
                        .build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "Hello, from styx server!"
                        }

                threadCount("http-boss-executor") shouldBe 1
                threadCount("http-worker-executor") shouldBe 1
            }

            scenario("Terminates executor threads when server shuts down") {
                styxServer.stop()
                threadCount("http-boss-executor") shouldBe 0
                threadCount("http-worker-executor") shouldBe 0
            }

            scenario("Overrides default global executors") {
                styxServer.restart(configuration = """
                    executors:
                      Styx-Client-Global-Worker:
                        type: NettyExecutor
                        config:
                          threads: 2
                          namePattern: new-styx-client-global
                          
                      StyxHttpServer-Global-Worker:
                        type: NettyExecutor
                        config:
                          threads: 2
                          namePattern: new-styx-server-worker
            
                    routingObjects:
                      proxyToOrigin:
                        type: HostProxy
                        config:
                          host: "localhost:${mockServer.port()}"

                    servers: 
                      http:
                        type: HttpServer
                        config:
                          port: 0
                          handler: proxyToOrigin
            
                    admin:
                      connectors:
                        http:
                          port: 0
                  """.trimIndent())

                eventually(2.seconds, AssertionError::class.java) {
                    styxServer().serverPort("http") shouldBeGreaterThan 0
                }
                val httpPort = styxServer().serverPort("http")

                (1..10).map { client.send(get("/").header(HOST, "localhost:$httpPort").build()) }
                        .forEach {
                            it.wait()!!
                                    .let { response ->
                                        response.status() shouldBe OK
                                        response.bodyAs(UTF_8) shouldBe "mock-server-01"
                                    }
                        }

                threadCount("new-styx-client-global") shouldBe 2
                threadCount("new-styx-server-worker") shouldBe 2
            }
        }
    }

    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider()

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
        mockServer.stop()
    }
}
