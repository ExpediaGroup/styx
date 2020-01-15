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
package com.hotels.styx.server

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.adminHostHeader
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import java.nio.charset.StandardCharsets.UTF_8


class ServerObjectSpec : FeatureSpec() {
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

                routingObjects:
                  secure:
                    type: StaticResponseHandler
                    config:
                      status: 200
                      content: "secure"
                      
                  nonSecure:
                    type: StaticResponseHandler
                    config:
                      status: 200
                      content: "non-secure"
                      
                servers:
                  myHttp:
                    type: HttpServer
                    config:
                      port: 0
                      handler: nonSecure
                        
                  myHttps:
                    type: HttpServer
                    config:
                      port: 0
                      handler: secure
                      tlsSettings:
                        certificateFile: $crtFile
                        certificateKeyFile: $keyFile
                        sslProvider: JDK

                httpPipeline:
                  type: StaticResponseHandler
                  config:
                    status: 200
                """.trimIndent())

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

    init {
        feature("Styx HTTP servers") {
            scenario("Start accepting traffic after plugins are loaded") {

                styxServer.restart()

                // 1. Query server addresses from the admin interface
                val httpPort = testClient.send(get("/admin/servers/myHttp/port").header(HOST, styxServer().adminHostHeader()).build())
                        .wait()
                        .bodyAs(UTF_8)
                        .toInt()

                val httpsPort = testClient.send(get("/admin/servers/myHttps/port").header(HOST, styxServer().adminHostHeader()).build())
                        .wait()
                        .bodyAs(UTF_8)
                        .toInt()

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
            }
        }
    }
}

private val crtFile = ResourcePaths.fixturesHome(ServerObjectSpec::class.java, "/ssl/testCredentials.crt").toString()
private val keyFile = ResourcePaths.fixturesHome(ServerObjectSpec::class.java, "/ssl/testCredentials.key").toString()
