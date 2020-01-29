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
package com.hotels.styx.routing.handlers

import com.hotels.styx.api.ByteStream
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.client.StyxHostHttpClient
import com.hotels.styx.client.applications.metrics.OriginMetrics
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.handle
import com.hotels.styx.requestContext
import com.hotels.styx.routingObjectDef
import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono

class HostProxyTest : FeatureSpec() {
    val request = HttpRequest.get("/").build()
    var client: StyxHostHttpClient? = null

    init {
        feature("Routing and proxying") {
            scenario("Proxies traffic") {
                HostProxy("localhost", 80, client, mockk()).handle(request.stream(), mockk())

                verify {
                    client!!.sendRequest(ofType(LiveHttpRequest::class))
                }
            }

            scenario("Requests arriving at stopped HostProxy object") {
                val exception = HostProxy("localhost", 80, client, mockk()).let {
                    it.stop()

                    shouldThrow<IllegalStateException> {
                        it.handle(request)
                                .toMono()
                                .block()
                    }
                }

                exception.message shouldBe ("HostProxy localhost:80 is stopped but received traffic.")

                verify(exactly = 0) {
                    client!!.sendRequest(any())
                }
            }

            scenario("Updates metrics for cancelled responses") {
                val client = mockk<StyxHostHttpClient>()
                val originMetrics = mockk<OriginMetrics>()

                every { client.sendRequest(any()) } returns Eventual(Mono.never())

                val hostProxy = HostProxy("abc", 80, client, originMetrics)

                hostProxy.handle(get("/").build())
                        .toMono()
                        .subscribe()
                        .dispose()

                verify(exactly = 1) { originMetrics.requestCancelled() }
            }

            scenario("Updates metrics for cancelled response content") {
                val client = mockk<StyxHostHttpClient>()
                val originMetrics = mockk<OriginMetrics>()

                every { client.sendRequest(any()) } returns Eventual.of(
                        LiveHttpResponse
                                .response()
                                .body(ByteStream(Flux.never()))
                                .build())

                val hostProxy = HostProxy("abc", 80, client, originMetrics)

                hostProxy.handle(LiveHttpRequest.get("/").build(), requestContext())
                        .toMono()
                        .block()
                        .let { response ->
                            response!!.body()
                                    .toFlux()
                                    .subscribe()
                                    .dispose()
                        }

                verify(exactly = 1) { originMetrics.requestCancelled() }
            }

        }

        feature("HostProxy.Factory") {
            val context = RoutingObjectFactoryContext()

            scenario("Uses configured host and port number") {
                val factory = HostProxy.Factory()

                val hostProxy = factory.build(listOf("hostproxyname"), context.get(), routingObjectDef("""
                          type: HostProxy
                          config:
                            host: ahost.server.com:1234
                        """.trimIndent())) as HostProxy

                hostProxy.host shouldBe "ahost.server.com"
                hostProxy.port shouldBe  1234
            }

            scenario("Port defaults to 80") {
                val factory = HostProxy.Factory()

                val hostProxy = factory.build(listOf("hostproxyname"), context.get(), routingObjectDef("""
                          type: HostProxy
                          config:
                            host: localhost
                        """.trimIndent())) as HostProxy

                hostProxy.host shouldBe "localhost"
                hostProxy.port shouldBe  80
            }

            scenario("Port defaults to 443 when TLS settings are present") {
                val factory = HostProxy.Factory()

                val hostProxy = factory.build(listOf("hostproxyname"), context.get(), routingObjectDef("""
                          type: HostProxy
                          config:
                            host: localhost
                            tlsSettings:
                              trustAllCerts: true
                        """.trimIndent())) as HostProxy

                hostProxy.host shouldBe "localhost"
                hostProxy.port shouldBe 443
            }
        }
    }

    override fun beforeTest(testCase: TestCase) {
        client = mockk(relaxed = true) {
            every { sendRequest(any()) } returns Eventual.of(HttpResponse.response(OK).build().stream())
        }
    }

}