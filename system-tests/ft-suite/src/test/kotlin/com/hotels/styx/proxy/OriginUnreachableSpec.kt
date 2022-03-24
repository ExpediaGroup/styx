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
package com.hotels.styx.proxy

import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.testClient
import com.hotels.styx.support.wait
import io.kotest.core.spec.Spec
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.FeatureSpec

class OriginUnreachableSpec : FeatureSpec() {
    val client: StyxHttpClient = StyxHttpClient.Builder().build()

    val styxServer = StyxServerProvider()

    override fun afterSpec(spec: Spec) {
        styxServer.stop()
    }

    init {
        feature("Responds with 502 with application ID in X-Styx-Origin-Id header") {
            scenario("HostProxy is unable to connect to an origin") {
                styxServer.restart("""
                    proxy:
                      connectors:
                        http:
                          port: 0
                      
                    admin:
                      connectors:
                        http:
                          port: 0
            
                    routingObjects:
                      remoteHost:
                        type: HostProxy
                        tags:
                          - lbGroup=zone1
                        config:
                          host: "localhost:27341"
                    
                      zone1Lb:
                        type: LoadBalancingGroup
                        config:
                          origins: zone1

                    httpPipeline: zone1Lb
                  """.trimIndent())

                // Note: Since https://github.com/HotelsDotCom/styx/pull/337, the connection pool
                // handles OriginUnreachableException internally, and it no longer propagates to
                // Styx proxy server.
                //
                // Therefore, Styx proxy server now sees flavour of ResourceExhaustedException which
                // maps to a 503 SERVICE UNAVAILABLE response.

                testClient.send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                        .wait()!!
                        .let {
                            it.status() shouldBe (SERVICE_UNAVAILABLE)
                            it.header("X-Styx-Origin-Id").get() shouldBe "remoteHost"
                        }
            }
        }


    }

}


