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
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.common.FreePorts.freePort
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import io.kotlintest.Spec
import io.kotlintest.specs.StringSpec
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import org.slf4j.LoggerFactory.getLogger
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.support.Support.requestContext
import io.kotlintest.shouldBe

// todo remove K from name after scala test deleted
class RetryHandlingKSpec : StringSpec() {
    val LOGGER = getLogger(RetryHandlingKSpec::class.java)

    val response = "Response From localhost"

    val meterRegistry = CompositeMeterRegistry()
    val metrics = CentralisedMetrics(MicrometerRegistry(meterRegistry))

    val server1 = FakeHttpServer(0, "app", "HEALTHY_ORIGIN_ONE")
    val server2 = FakeHttpServer(0, "app", "HEALTHY_ORIGIN_TWO")

    lateinit var healthyOriginOne: Origin
    lateinit var healthyOriginTwo: Origin

    val originServer1 = FakeHttpServer(0, "app", "ORIGIN_ONE")
    val originServer2 = FakeHttpServer(0, "app", "ORIGIN_TWO")
    val originServer3 = FakeHttpServer(0, "app", "ORIGIN_THREE")
    val originServer4 = FakeHttpServer(0, "app", "ORIGIN_FOUR")

    lateinit var originOne: Origin
    lateinit var originTwo: Origin
    lateinit var originThree: Origin
    lateinit var originFour: Origin

    val unhealthyOriginOne: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_ONE").build()
    val unhealthyOriginTwo: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_TWO").build()
    val unhealthyOriginThree: Origin = newOriginBuilder("localhost", freePort()).id("UNHEALTHY_ORIGIN_THREE").build()

    override fun beforeSpec(spec: Spec) {
        server1.start()
        healthyOriginOne = originFrom(server1)

        server1.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH.toString(), response.toByteArray(Charset.defaultCharset()).size.toString())
                .withHeader("Stub-Origin-Info", healthyOriginOne.applicationInfo())
                .withBody(response)
        )

        server2.start()
        healthyOriginTwo = originFrom(server2)
        server2.stub(
            urlStartingWith("/"), aResponse()
                .withStatus(200)
                .withHeader(CONTENT_LENGTH.toString(), response.toByteArray(Charset.defaultCharset()).size.toString())
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
                        .pendingConnectionTimeout(10, TimeUnit.SECONDS)
                        .build()
                )
                .build()

            val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .retryPolicy(RetryNTimes(3))
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val response = Mono.from(client.sendRequest(get("/version.txt").build(), requestContext())).block()
            response!!
            response.status() shouldBe OK
        }

        "propagates the last observed exception if all retries failed" {
            val backendService = BackendService.Builder()
                .origins(unhealthyOriginOne, unhealthyOriginTwo, unhealthyOriginThree)
                .build()
            val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id())
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

            val client: StyxBackendServiceClient = newHttpClientBuilder(backendService.id())
                .metrics(metrics)
                .retryPolicy(RetryNTimes(3))
                .loadBalancer(stickySessionStrategy(activeOrigins(backendService)))
                .build()

            val request: LiveHttpRequest = get("/version.txt").build()

            val response = Mono.from(client.sendRequest(request, requestContext())).block()
            response!!
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