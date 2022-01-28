/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.Id.id
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.StickySessionConfig.newStickySessionConfigBuilder
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.support.Support.requestContext
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import java.nio.charset.Charset.defaultCharset
import java.util.concurrent.TimeUnit.SECONDS

class StickySessionSpec : StringSpec() {
    private val meterRegistry = CompositeMeterRegistry()
    private val metrics = CentralisedMetrics(MicrometerRegistry(meterRegistry))

    private val server1 = FakeHttpServer(0, "app", "app-01")
    private val server2 = FakeHttpServer(0, "app", "app-02")

    private lateinit var appOriginOne: Origin
    private lateinit var appOriginTwo: Origin
    private lateinit var backendService: BackendService

    override fun beforeTest(testCase: TestCase) {
        server1.start()
        server2.start()

        appOriginOne = originFrom(server1)
        appOriginTwo = originFrom(server2)

        val response = "Response From localhost"

        server1.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH, response.toByteArray(defaultCharset()).size)
                .withHeader("Stub-Origin-Info", appOriginOne.applicationInfo())
                .withBody(response)
        )

        server2.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH, response.toByteArray(defaultCharset()).size)
                .withHeader("Stub-Origin-Info", appOriginTwo.applicationInfo())
                .withBody(response)
        )

        backendService = BackendService.Builder()
            .id(id("app"))
            .origins(appOriginOne, appOriginTwo)
            .build()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        server1.stop()
        server2.stop()
    }

    init {
        "Responds with sticky session cookie when STICKY_SESSION_ENABLED=true" {
            val stickySessionConfig = newStickySessionConfigBuilder().timeout(100, SECONDS).build()

            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .stickySessionConfig(stickySessionConfig)
                .build()

            val request = LiveHttpRequest.get("/").build()

            val response = client.sendRequest(request, requestContext()).await()
            response.status() shouldBe OK
            val cookie = response.cookie("styx_origin_app").get()
            cookie.value() shouldMatch Regex("app-0[12]")

            cookie.path().get() shouldBe "/"
            cookie.httpOnly() shouldBe true

            cookie.maxAge().isPresent shouldBe true
            cookie.maxAge().get() shouldBe 100L
        }

        "Responds without sticky session cookie when sticky session is not enabled" {
            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(roundRobinStrategy(activeOrigins(backendService)))
                .build()

            val request = LiveHttpRequest.get("/").build()

            val response = client.sendRequest(request, requestContext()).await()
            response.status() shouldBe OK
            response.cookies().size shouldBe 0
        }

        "Routes to origins indicated by sticky session cookie." {
            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val request = LiveHttpRequest.get("/")
                .cookies(requestCookie("styx_origin_app", "app-02"))
                .build()

            val response1 = client.sendRequest(request, requestContext()).await()
            val response2 = client.sendRequest(request, requestContext()).await()
            val response3 = client.sendRequest(request, requestContext()).await()

            response1.header("Stub-Origin-Info").get() shouldBe "APP-localhost:${server2.port()}"
            response2.header("Stub-Origin-Info").get() shouldBe "APP-localhost:${server2.port()}"
            response3.header("Stub-Origin-Info").get() shouldBe "APP-localhost:${server2.port()}"
        }

        "Routes to origins indicated by sticky session cookie when other cookies are provided." {
            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val request = LiveHttpRequest.get("/")
                .cookies(
                    requestCookie("other_cookie1", "foo"),
                    requestCookie("styx_origin_app", "app-02"),
                    requestCookie("other_cookie2", "bar")
                )
                .build()

            val response1 = client.sendRequest(request, requestContext()).await()
            val response2 = client.sendRequest(request, requestContext()).await()
            val response3 = client.sendRequest(request, requestContext()).await()

            response1.header("Stub-Origin-Info").get() shouldBe ("APP-localhost:${server2.port()}")
            response2.header("Stub-Origin-Info").get() shouldBe ("APP-localhost:${server2.port()}")
            response3.header("Stub-Origin-Info").get() shouldBe ("APP-localhost:${server2.port()}")
        }

        "Routes to new origin when the origin indicated by sticky session cookie does not exist." {
            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val request = LiveHttpRequest.get("/")
                .cookies(requestCookie("styx_origin_app", "h3"))
                .build()

            val response = client.sendRequest(request, requestContext()).await()

            response.status() shouldBe OK
            response.cookies().size shouldBe 1

            val cookie = response.cookie("styx_origin_app").get()

            cookie.value() shouldMatch Regex("app-0[12]")

            cookie.path().get() shouldBe ("/")
            cookie.httpOnly() shouldBe (true)
            cookie.maxAge().isPresent shouldBe (true)
        }
    }

    fun activeOrigins(backendService: BackendService): ActiveOrigins =
        newOriginsInventoryBuilder(CentralisedMetrics(MicrometerRegistry(meterRegistry)), backendService).build()

    private fun roundRobinStrategy(activeOrigins: ActiveOrigins): LoadBalancer = RoundRobinStrategy(activeOrigins, activeOrigins.snapshot())

    private fun stickySessionStrategy(activeOrigins: ActiveOrigins) = StickySessionLoadBalancingStrategy(activeOrigins, roundRobinStrategy(activeOrigins))
}
