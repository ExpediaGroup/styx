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
package com.hotels.styx.server

import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.routing.handlers2.PathPrefixRouter
import com.hotels.styx.routing.handlers2.RefLookup
import com.hotels.styx.routing.handlers2.Route
import com.hotels.styx.routing.handlers2.StaticResponse
import com.hotels.styx.servers.StyxHttpServer
import com.hotels.styx.servers.StyxHttpServerTlsSettings
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8


class ServerApiObjectSpec : FeatureSpec() {

    val styxServer = StyxServer(StyxServerComponents.Builder()
            .styxConfig(StyxConfig.fromYaml("""
                ---
                admin:
                  connectors:
                    http:
                      port: 0 
                """.trimIndent(), true))
            .routingObject("secure", StaticResponse(status = 200, content = "secure"))
            .routingObject("nonSecure", StaticResponse(status = 200, content = "non-secure"))
            .routingObject("nonSecure2", StaticResponse(status = 200, content = "non-secure 2"))
            .routingObject("pathMapper",
                    PathPrefixRouter(listOf(
                            Route("/", RefLookup("nonSecure")),
                            Route("/2/", RefLookup("nonSecure2"))
                    )))

            .server("myHttp", StyxHttpServer(port = 0, handler = "nonSecure"))
            .server("pathPrefixServer", StyxHttpServer(port = 0, handler = "pathMapper"))
            .server("myHttps",
                    StyxHttpServer(
                            port = 0,
                            handler = "secure",
                            tlsSettings = StyxHttpServerTlsSettings(
                                    certificateFile = crtFile,
                                    certificateKeyFile = keyFile,
                                    sslProvider = "JDK"
                            )))
            .build())


    override fun afterSpec(spec: Spec) {
        styxServer.stopAsync().awaitTerminated()
    }

    init {
        feature("Styx HTTP servers") {
            styxServer.startAsync().awaitRunning()

            scenario("Start accepting traffic after plugins are loaded") {

                // 1. Query server addresses from the admin interface
                val httpPort = eventually(2.seconds) {
                    testClient.send(get("/admin/servers/myHttp/port").header(HOST, styxServer.adminHostHeader()).build())
                            .wait()
                            .bodyAs(UTF_8)
                            .toInt()
                }

                val httpsPort = eventually(2.seconds) {
                    testClient.send(get("/admin/servers/myHttps/port").header(HOST, styxServer.adminHostHeader()).build())
                            .wait()
                            .bodyAs(UTF_8)
                            .toInt()
                }

                val httpPort2 = eventually(2.seconds) {
                    testClient.send(get("/admin/servers/pathPrefixServer/port").header(HOST, styxServer.adminHostHeader()).build())
                            .wait()
                            .bodyAs(UTF_8)
                            .toInt()
                }

                // 2. Send a probe to both of them
                testClient.send(get("/").header(HOST, "localhost:$httpPort").build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "non-secure"
                        }

                testClient.secure().send(get("/").header(HOST, "localhost:$httpsPort").build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "secure"
                        }


                testClient.send(get("/1/bar").header(HOST, "localhost:$httpPort2").build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "non-secure"
                        }

                testClient.send(get("/2/bar").header(HOST, "localhost:$httpPort2").build())
                        .wait()!!
                        .let {
                            it.status() shouldBe OK
                            it.bodyAs(UTF_8) shouldBe "non-secure 2"
                        }
            }
        }
    }
}

private val crtFile = ResourcePaths.fixturesHome(ServerObjectSpec::class.java, "/ssl/testCredentials.crt").toString()
private val keyFile = ResourcePaths.fixturesHome(ServerObjectSpec::class.java, "/ssl/testCredentials.key").toString()
