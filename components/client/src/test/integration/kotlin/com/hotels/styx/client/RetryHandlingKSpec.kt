package com.hotels.styx.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.common.FreePorts.freePort
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.support.Support.requestContext
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import reactor.core.publisher.Mono
import java.nio.charset.Charset.defaultCharset
import java.util.concurrent.TimeUnit.SECONDS

// todo remove K from name after scala test deleted
class RetryHandlingKSpec : StringSpec() {
    private val response = "Response From localhost"

    private val meterRegistry = CompositeMeterRegistry()
    private val metrics = CentralisedMetrics(MicrometerRegistry(meterRegistry))

    private val server1 = FakeHttpServer(0, "app", "HEALTHY_ORIGIN_ONE")
    private val server2 = FakeHttpServer(0, "app", "HEALTHY_ORIGIN_TWO")

    private val originServer1 = FakeHttpServer(0, "app", "ORIGIN_ONE")
    private val originServer2 = FakeHttpServer(0, "app", "ORIGIN_TWO")
    private val originServer3 = FakeHttpServer(0, "app", "ORIGIN_THREE")
    private val originServer4 = FakeHttpServer(0, "app", "ORIGIN_FOUR")

    private val unhealthyOriginOne: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_ONE").build()
    private val unhealthyOriginTwo: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_TWO").build()
    private val unhealthyOriginThree: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_THREE").build()

    private lateinit var healthyOriginOne: Origin
    private lateinit var healthyOriginTwo: Origin

    private lateinit var originOne: Origin
    private lateinit var originTwo: Origin
    private lateinit var originThree: Origin
    private lateinit var originFour: Origin

    override fun beforeSpec(spec: Spec) {
        server1.start()
        healthyOriginOne = originFrom(server1)

        server1.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH, response.toByteArray(defaultCharset()).size)
                .withHeader("Stub-Origin-Info", healthyOriginOne.applicationInfo())
                .withBody(response)
        )

        server2.start()
        healthyOriginTwo = originFrom(server2)
        server2.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH, response.toByteArray(defaultCharset()).size)
                .withHeader("Stub-Origin-Info", healthyOriginOne.applicationInfo())
                .withBody(response)
        )

        originServer1.start()
        originOne = originFrom(originServer1)

        originServer2.start()
        originTwo = originFrom(originServer2)

        originServer3.start()
        originThree = originFrom(originServer3)

        originServer4.start()
        originFour = originFrom(originServer4)

        originServer2.start()
        originServer3.start()
        originServer4.start()
    }

    override fun afterSpec(spec: Spec) {
        server1.stop()
        server2.stop()
        originServer1.stop()
        originServer2.stop()
        originServer3.stop()
        originServer4.stop()
    }

    init {
        "retries the next available origin on failure" {
            val backendService = BackendService.Builder()
                .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
                .connectionPoolConfig(
                    ConnectionPoolSettings.Builder()
                        .pendingConnectionTimeout(10, SECONDS)
                        .build()
                )
                .build()

            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .retryPolicy(RetryNTimes(3))
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val response = client.sendRequest(LiveHttpRequest.get("/version.txt").build(), requestContext()).await()
            response.status() shouldBe OK
        }

        "propagates the last observed exception if all retries failed" {
            val backendService = BackendService.Builder()
                .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree)
                .build()

            newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .retryPolicy(RetryNTimes(2))
                .build()
        }

        "It should add sticky session id after a retry succeeded" {
            val stickySessionEnabled = StickySessionConfig.Builder()
                .enabled(true)
                .build()

            val backendService = BackendService.Builder()
                .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree, healthyOriginTwo)
                .stickySessionConfig(stickySessionEnabled)
                .build()

            val client = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .retryPolicy(RetryNTimes(3))
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val request = LiveHttpRequest.get("/version.txt").build()

            val response = client.sendRequest(request, requestContext()).await()
            val cookie = response.cookie("styx_origin_generic-app").get()

            cookie.value() shouldBe "HEALTHY_ORIGIN_TWO"
            cookie.path().get() shouldBe "/"
            cookie.httpOnly() shouldBe true
            cookie.maxAge().isPresent shouldBe true
        }
    }

    private fun activeOrigins(backendService: BackendService) =
        newOriginsInventoryBuilder(CentralisedMetrics(MicrometerRegistry(meterRegistry)), backendService).build()

    private fun stickySessionStrategy(activeOrigins: ActiveOrigins) = StickySessionLoadBalancingStrategy(
        activeOrigins,
        RoundRobinStrategy(activeOrigins, activeOrigins.snapshot())
    )
}
