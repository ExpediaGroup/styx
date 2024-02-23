/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.hotels.styx.Environment
import com.hotels.styx.StyxConfig
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.Id.GENERIC_APP
import com.hotels.styx.api.Id.id
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.BackendService.Companion.newBackendServiceBuilder
import com.hotels.styx.api.extension.service.StickySessionConfig.newStickySessionConfigBuilder
import com.hotels.styx.client.OriginStatsFactory
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory
import com.hotels.styx.client.ReactorBackendServiceClient
import com.hotels.styx.client.ReactorConnectionPool
import com.hotels.styx.client.ReactorHostHttpClient
import com.hotels.styx.client.ReactorOriginsInventory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.netty.handler.ssl.SslContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.resources.LoopResources
import kotlin.jvm.optionals.getOrNull

class ReactorBackendServiceClientFactoryTest : StringSpec() {
    private lateinit var environment: Environment
    private lateinit var backendService: BackendService
    private val eventLoopGroup: LoopResources = mockk()
    private val originStatsFactory: OriginStatsFactory = mockk()
    private val sslContext: SslContext = mockk()
    private val connectionPool: ReactorConnectionPool = mockk()
    private val context: HttpInterceptor.Context = mockk()

    init {
        beforeTest {
            environment = Environment.Builder().registry(MicrometerRegistry(SimpleMeterRegistry())).build()
            backendService =
                newBackendServiceBuilder()
                    .origins(Origin.newOriginBuilder("localhost", 8081).build())
                    .build()

            every { connectionPool.isHttp2() } returns false
        }

        "createClient" {
            val originsInventory =
                ReactorOriginsInventory.Builder(backendService.id())
                    .metrics(environment.centralisedMetrics())
                    .eventLoopGroup(eventLoopGroup)
                    .initialOrigins(backendService.origins())
                    .originStatsFactory(originStatsFactory)
                    .sslContext(sslContext)
                    .connectionPool(connectionPool)
                    .build()
            val client =
                ReactorBackendServiceClientFactory(environment)
                    .createClient(backendService, originsInventory, originStatsFactory)

            client.shouldBeInstanceOf<ReactorBackendServiceClient>()
        }

        "uses the origin specified in the sticky session cookie" {
            val backendService =
                newBackendServiceBuilder()
                    .origins(
                        Origin.newOriginBuilder("localhost", 9091).id("x").build(),
                        Origin.newOriginBuilder("localhost", 9092).id("y").build(),
                        Origin.newOriginBuilder("localhost", 9093).id("z").build(),
                    )
                    .stickySessionConfig(newStickySessionConfigBuilder().enabled(true).build())
                    .build()
            val originsInventory =
                ReactorOriginsInventory.Builder(backendService.id())
                    .metrics(environment.centralisedMetrics())
                    .eventLoopGroup(eventLoopGroup)
                    .initialOrigins(backendService.origins())
                    .hostClientFactory { origin, _, _, _, _, _, _, _, _, _, _ ->
                        if (origin.id() == id("x")) {
                            hostClient(response(OK).header("X-Origin-Id", "x").build())
                        } else if (origin.id() == id("y")) {
                            hostClient(response(OK).header("X-Origin-Id", "y").build())
                        } else {
                            hostClient(response(OK).header("X-Origin-Id", "z").build())
                        }
                    }
                    .originStatsFactory(originStatsFactory)
                    .sslContext(sslContext)
                    .connectionPool(connectionPool)
                    .build()

            val client =
                ReactorBackendServiceClientFactory(environment)
                    .createClient(backendService, originsInventory, CachingOriginStatsFactory(environment.centralisedMetrics()))

            val requestX = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("x").toString())).build()
            val requestY = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("y").toString())).build()
            val requestZ = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("z").toString())).build()

            val responseX = Mono.from(client.sendRequest(requestX, context)).block()
            val responseY = Mono.from(client.sendRequest(requestY, context)).block()
            val responseZ = Mono.from(client.sendRequest(requestZ, context)).block()

            responseX!!.header("X-Origin-Id").getOrNull() shouldBe "x"
            responseY!!.header("X-Origin-Id").getOrNull() shouldBe "y"
            responseZ!!.header("X-Origin-Id").getOrNull() shouldBe "z"
        }

        "uses the origin specified in the origin restriction cookie" {
            val config = MapBackedConfiguration()
            config["originRestrictionCookie"] = ORIGINS_RESTRICTION_COOKIE

            environment =
                Environment.Builder()
                    .registry(MicrometerRegistry(SimpleMeterRegistry()))
                    .configuration(StyxConfig(config))
                    .build()

            val backendService =
                newBackendServiceBuilder()
                    .origins(
                        Origin.newOriginBuilder("localhost", 9091).id("x").build(),
                        Origin.newOriginBuilder("localhost", 9092).id("y").build(),
                        Origin.newOriginBuilder("localhost", 9093).id("z").build(),
                    )
                    .build()
            val originsInventory =
                ReactorOriginsInventory.Builder(backendService.id())
                    .metrics(environment.centralisedMetrics())
                    .eventLoopGroup(eventLoopGroup)
                    .initialOrigins(backendService.origins())
                    .hostClientFactory { origin, _, _, _, _, _, _, _, _, _, _ ->
                        if (origin.id() == id("x")) {
                            hostClient(response(OK).header("X-Origin-Id", "x").build())
                        } else if (origin.id() == id("y")) {
                            hostClient(response(OK).header("X-Origin-Id", "y").build())
                        } else {
                            hostClient(response(OK).header("X-Origin-Id", "z").build())
                        }
                    }
                    .originStatsFactory(originStatsFactory)
                    .sslContext(sslContext)
                    .connectionPool(connectionPool)
                    .build()

            val client =
                ReactorBackendServiceClientFactory(environment)
                    .createClient(backendService, originsInventory, CachingOriginStatsFactory(environment.centralisedMetrics()))

            val requestX = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("x").toString())).build()
            val requestY = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("y").toString())).build()
            val requestZ = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("z").toString())).build()

            val responseX = Mono.from(client.sendRequest(requestX, context)).block()
            val responseY = Mono.from(client.sendRequest(requestY, context)).block()
            val responseZ = Mono.from(client.sendRequest(requestZ, context)).block()

            responseX!!.header("X-Origin-Id").getOrNull() shouldBe "x"
            responseY!!.header("X-Origin-Id").getOrNull() shouldBe "y"
            responseZ!!.header("X-Origin-Id").getOrNull() shouldBe "z"
        }
    }

    private fun hostClient(response: LiveHttpResponse): ReactorHostHttpClient {
        val mockClient: ReactorHostHttpClient = mockk()
        every { mockClient.sendRequest(any(), any()) } returns Flux.just(response)
        every { mockClient.loadBalancingMetric() } returns LoadBalancingMetric(1)
        return mockClient
    }

    companion object {
        private const val ORIGINS_RESTRICTION_COOKIE = "styx-origins-restriction"
        private val STICKY_COOKIE = "styx_origin_$GENERIC_APP"
    }
}
