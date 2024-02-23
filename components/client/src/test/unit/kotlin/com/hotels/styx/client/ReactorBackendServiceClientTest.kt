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
package com.hotels.styx.client

import com.google.common.net.HostAndPort
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpHeaderNames.CHUNKED
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.NOT_IMPLEMENTED
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.HttpResponseStatus.UNAUTHORIZED
import com.hotels.styx.api.Id.GENERIC_APP
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.api.MeterRegistry
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.exceptions.OriginUnreachableException
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.RemoteHost
import com.hotels.styx.api.extension.RemoteHost.remoteHost
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.StickySessionConfig
import com.hotels.styx.client.retry.RetryNTimes
import com.hotels.styx.metrics.CentralisedMetrics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class ReactorBackendServiceClientTest : StringSpec() {
    private val context: HttpInterceptor.Context = mockk()
    private val backendService: BackendService =
        backendBuilderWithOrigins(SOME_ORIGIN.port())
            .stickySessionConfig(STICKY_SESSION_CONFIG)
            .build()
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var metrics: CentralisedMetrics

    init {
        beforeTest {
            meterRegistry = MicrometerRegistry(SimpleMeterRegistry())
            metrics = CentralisedMetrics(meterRegistry)
        }

        "sendRequest routes the request to host selected by load balancer" {
            val hostClient = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(
                                remoteHost(
                                    SOME_ORIGIN,
                                    toHandler(hostClient),
                                    hostClient,
                                ),
                            ),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response!!.status() shouldBe OK
            verify { hostClient.sendRequest(SOME_REQ, any()) }
        }

        "constructs retry context when load balancer does not find available origins" {
            val retryContextSlot = slot<RetryPolicy.Context>()
            val loadBalancerSlot = slot<LoadBalancer>()
            val lbPreferencesSlot = slot<LoadBalancer.Preferences>()
            val retryPolicy =
                mockRetryPolicy(
                    retryContextSlot,
                    loadBalancerSlot,
                    lbPreferencesSlot,
                    true,
                    true,
                    true,
                )
            val hostClient = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.empty(),
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = retryPolicy,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            retryContextSlot.isCaptured shouldBe true
            retryContextSlot.captured.appId() shouldBe backendService.id()
            retryContextSlot.captured.currentRetryCount() shouldBe 1
            retryContextSlot.captured.lastException() shouldBe Optional.empty()

            loadBalancerSlot.isCaptured shouldBe true
            loadBalancerSlot.captured shouldNotBe null

            lbPreferencesSlot.isCaptured shouldBe true
            lbPreferencesSlot.captured.avoidOrigins() shouldBe emptyList()
            lbPreferencesSlot.captured.preferredOrigins() shouldBe Optional.empty()

            response.status() shouldBe OK
        }

        "retries when retry policy tells to retry" {
            val retryContextSlot = slot<RetryPolicy.Context>()
            val loadBalancerSlot = slot<LoadBalancer>()
            val lbPreferencesSlot = slot<LoadBalancer.Preferences>()
            val retryPolicy =
                mockRetryPolicy(
                    retryContextSlot,
                    loadBalancerSlot,
                    lbPreferencesSlot,
                    true,
                    false,
                )

            val hostClient1 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_1, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient2 = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(ORIGIN_1, toHandler(hostClient1), hostClient1)),
                            Optional.of(remoteHost(ORIGIN_2, toHandler(hostClient2), hostClient2)),
                        ),
                    retryPolicy = retryPolicy,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            retryContextSlot.isCaptured shouldBe true
            retryContextSlot.captured.appId() shouldBe backendService.id()
            retryContextSlot.captured.currentRetryCount() shouldBe 1
            retryContextSlot.captured.lastException().getOrNull().shouldBeInstanceOf<OriginUnreachableException>()

            loadBalancerSlot.isCaptured shouldBe true
            loadBalancerSlot.captured shouldNotBe null

            lbPreferencesSlot.isCaptured shouldBe true
            lbPreferencesSlot.captured.avoidOrigins() shouldBe listOf(ORIGIN_1)
            lbPreferencesSlot.captured.preferredOrigins() shouldBe Optional.empty()

            response.status() shouldBe OK

            verifyOrder {
                hostClient1.sendRequest(SOME_REQ, any())
                hostClient2.sendRequest(SOME_REQ, any())
            }
        }

        "stops retries when retry policy tells to stop" {
            val hostClient1 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_1, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient2 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_2, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient3 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_2, RuntimeException("An error occurred")),
                    ),
                )

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(ORIGIN_1, toHandler(hostClient1), hostClient1)),
                            Optional.of(remoteHost(ORIGIN_2, toHandler(hostClient2), hostClient2)),
                            Optional.of(remoteHost(ORIGIN_3, toHandler(hostClient3), hostClient3)),
                        ),
                    retryPolicy = mockRetryPolicy(true, false),
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            StepVerifier.create(backendServiceClient.sendRequest(SOME_REQ, context))
                .verifyError(OriginUnreachableException::class.java)

            verifyOrder {
                hostClient1.sendRequest(SOME_REQ, any())
                hostClient2.sendRequest(SOME_REQ, any())
                hostClient3.sendRequest(SOME_REQ, any()) wasNot Called
            }
        }

        "retries at most 3 times" {
            val hostClient1 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_1, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient2 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_2, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient3 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_3, RuntimeException("An error occurred")),
                    ),
                )
            val hostClient4 =
                mockHostClient(
                    Mono.error(
                        OriginUnreachableException(ORIGIN_4, RuntimeException("An error occurred")),
                    ),
                )

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(ORIGIN_1, toHandler(hostClient1), hostClient1)),
                            Optional.of(remoteHost(ORIGIN_2, toHandler(hostClient2), hostClient2)),
                            Optional.of(remoteHost(ORIGIN_3, toHandler(hostClient3), hostClient3)),
                            Optional.of(remoteHost(ORIGIN_4, toHandler(hostClient4), hostClient4)),
                        ),
                    retryPolicy = mockRetryPolicy(true, true, true, true),
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            StepVerifier.create(backendServiceClient.sendRequest(SOME_REQ, context))
                .verifyError(NoAvailableHostsException::class.java)

            verifyOrder {
                hostClient1.sendRequest(SOME_REQ, any())
                hostClient2.sendRequest(SOME_REQ, any())
                hostClient3.sendRequest(SOME_REQ, any())
                hostClient4.sendRequest(SOME_REQ, any()) wasNot Called
            }
        }

        "increments response status metrics for bad response" {
            val hostClient = mockHostClient(Mono.just(response(BAD_REQUEST).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response.status() shouldBe BAD_REQUEST
            verify { hostClient.sendRequest(SOME_REQ, any()) }
            meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "400").counter() shouldNotBe null
        }

        "increments response status metrics for 401" {
            val hostClient = mockHostClient(Mono.just(response(UNAUTHORIZED).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response.status() shouldBe UNAUTHORIZED
            verify { hostClient.sendRequest(SOME_REQ, any()) }
            meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "401").counter() shouldNotBe null
        }

        "increments response status metrics for 500" {
            val hostClient = mockHostClient(Mono.just(response(INTERNAL_SERVER_ERROR).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response.status() shouldBe INTERNAL_SERVER_ERROR
            verify { hostClient.sendRequest(SOME_REQ, any()) }
            meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "500").counter() shouldNotBe null
        }

        "increments response status metrics for 501" {
            val hostClient = mockHostClient(Mono.just(response(NOT_IMPLEMENTED).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response.status() shouldBe NOT_IMPLEMENTED
            verify { hostClient.sendRequest(SOME_REQ, any()) }
            meterRegistry.get("proxy.client.responseCode.errorStatus").tag("statusCode", "501").counter() shouldNotBe null
        }

        "removes bad content length" {
            val hostClient =
                mockHostClient(
                    Mono.just(
                        response(OK)
                            .addHeader(CONTENT_LENGTH, 50)
                            .addHeader(TRANSFER_ENCODING, CHUNKED)
                            .build(),
                    ),
                )

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(SOME_ORIGIN, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response = Mono.from(backendServiceClient.sendRequest(SOME_REQ, context)).block()

            response.status() shouldBe OK
            response.contentLength().isPresent shouldBe false
            response.header(TRANSFER_ENCODING).get() shouldBe "chunked"
        }

        "prefers sticky origins" {
            val lbPreferencesSlot = slot<LoadBalancer.Preferences>()
            val origin = originWithId("localhost:234", "App-X", "Origin-Y")
            val hostClient = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = null,
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            lbPreferencesSlot,
                            Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response =
                Mono.from(
                    backendServiceClient
                        .sendRequest(
                            get("/foo")
                                .cookies(requestCookie("styx_origin_$GENERIC_APP", "Origin-Y"))
                                .build(),
                            context,
                        ),
                ).block()

            response.status() shouldBe OK

            lbPreferencesSlot.isCaptured shouldBe true
            lbPreferencesSlot.captured.preferredOrigins() shouldBe Optional.of("Origin-Y")
        }

        "prefers restricted origins" {
            val lbPreferencesSlot = slot<LoadBalancer.Preferences>()
            val origin = originWithId("localhost:234", "App-X", "Origin-Y")
            val hostClient = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "restrictedOrigin",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            lbPreferencesSlot,
                            Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response =
                Mono.from(
                    backendServiceClient
                        .sendRequest(
                            get("/foo")
                                .cookies(requestCookie("restrictedOrigin", "Origin-Y"))
                                .build(),
                            context,
                        ),
                ).block()

            response.status() shouldBe OK

            lbPreferencesSlot.isCaptured shouldBe true
            lbPreferencesSlot.captured.preferredOrigins() shouldBe Optional.of("Origin-Y")
        }

        "prefers restricted origins over sticky origins when both are configured" {
            val lbPreferencesSlot = slot<LoadBalancer.Preferences>()
            val origin = originWithId("localhost:234", "App-X", "Origin-Y")
            val hostClient = mockHostClient(Mono.just(response(OK).build()))

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "restrictedOrigin",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            lbPreferencesSlot,
                            Optional.of(remoteHost(origin, toHandler(hostClient), hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = backendService.isOverrideHostHeader(),
                )

            val response =
                Mono.from(
                    backendServiceClient
                        .sendRequest(
                            get("/foo")
                                .cookies(
                                    requestCookie("restrictedOrigin", "Origin-Y"),
                                    requestCookie("styx_origin_$GENERIC_APP", "Origin-X"),
                                )
                                .build(),
                            context,
                        ),
                ).block()

            response.status() shouldBe OK

            lbPreferencesSlot.isCaptured shouldBe true
            lbPreferencesSlot.captured.preferredOrigins() shouldBe Optional.of("Origin-Y")
        }

        "host header is not over written when overrideHostHeader is false" {
            val hostClient = mockHostClient(Mono.just(response(OK).build()))
            val origin = newOriginBuilder(INCOMING_HOSTNAME, 9090).applicationId("app").build()
            val httpHandler: HttpHandler = mockk()

            every { httpHandler.handle(any(), any()) } returns Eventual.of(TEST_RESPONSE)

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "someCookie",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(origin, httpHandler, hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = false,
                )

            Mono.from(backendServiceClient.sendRequest(TEST_REQUEST, context)).block()

            verify { httpHandler.handle(TEST_REQUEST, context) }
        }

        "host header is not over written when overrideHostHeader is true" {
            val hostClient = mockHostClient(Mono.just(response(OK).build()))
            val origin = newOriginBuilder(UPDATED_HOSTNAME, 9090).applicationId("app").build()
            val httpHandler: HttpHandler = mockk()
            val updatedRequestSlot = slot<LiveHttpRequest>()

            every { httpHandler.handle(capture(updatedRequestSlot), any()) } returns Eventual.of(TEST_RESPONSE)

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "someCookie",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(origin, httpHandler, hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = true,
                )

            Mono.from(backendServiceClient.sendRequest(TEST_REQUEST, context)).block()

            updatedRequestSlot.captured.header(HOST).getOrNull() shouldBe UPDATED_HOSTNAME
        }

        "original requests is present in response when overrideHostHeader is false" {
            val hostClient = mockHostClient(Mono.just(response(OK).build()))
            val origin = newOriginBuilder(INCOMING_HOSTNAME, 9090).applicationId("app").build()
            val httpHandler: HttpHandler = mockk()

            every { httpHandler.handle(any(), any()) } returns Eventual.of(TEST_RESPONSE)

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "someCookie",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(origin, httpHandler, hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = false,
                )

            StepVerifier.create(backendServiceClient.sendRequest(TEST_REQUEST, context))
                .expectNextMatches { response ->
                    response.request().header(HOST).map { it == INCOMING_HOSTNAME }.orElse(false)
                }
                .verifyComplete()
        }

        "original requests is present in response when overrideHostHeader is true" {
            val hostClient = mockHostClient(Mono.just(response(OK).build()))
            val origin = newOriginBuilder(UPDATED_HOSTNAME, 9090).applicationId("app").build()
            val httpHandler: HttpHandler = mockk()

            every { httpHandler.handle(any(), any()) } returns Eventual.of(TEST_RESPONSE)

            val backendServiceClient =
                ReactorBackendServiceClient(
                    id = backendService.id(),
                    rewriteRuleset = RewriteRuleset(backendService.rewrites()),
                    originsRestrictionCookieName = "someCookie",
                    stickySessionConfig = backendService.stickySessionConfig(),
                    originIdHeader = StyxHeaderConfig.ORIGIN_ID_DEFAULT,
                    loadBalancer =
                        mockLoadBalancer(
                            Optional.of(remoteHost(origin, httpHandler, hostClient)),
                        ),
                    retryPolicy = DEFAULT_RETRY_POLICY,
                    metrics = metrics,
                    overrideHostHeader = true,
                )

            StepVerifier.create(backendServiceClient.sendRequest(TEST_REQUEST, context))
                .expectNextMatches { response ->
                    response.request().header(HOST).map { it == UPDATED_HOSTNAME }.orElse(false)
                }
                .verifyComplete()
        }
    }

    private fun toHandler(hostClient: ReactorHostHttpClient): HttpHandler =
        HttpHandler { request: LiveHttpRequest, ctx: HttpInterceptor.Context? ->
            Eventual(
                hostClient.sendRequest(
                    request,
                    ctx,
                ),
            )
        }

    private fun mockLoadBalancer(first: Optional<RemoteHost>): LoadBalancer {
        val lbStategy: LoadBalancer = mockk()
        every { lbStategy.choose(any()) } returns first
        return lbStategy
    }

    private fun mockLoadBalancer(
        first: Optional<RemoteHost>,
        vararg remoteHostWrappers: Optional<RemoteHost>,
    ): LoadBalancer {
        val lbStategy: LoadBalancer = mockk()
        every { lbStategy.choose(any()) } returns first andThenMany remoteHostWrappers.toList()
        return lbStategy
    }

    private fun mockLoadBalancer(
        lbPreferencesSlot: CapturingSlot<LoadBalancer.Preferences>,
        first: Optional<RemoteHost>,
        vararg remoteHostWrappers: Optional<RemoteHost>,
    ): LoadBalancer {
        val lbStategy: LoadBalancer = mockk()
        every { lbStategy.choose(capture(lbPreferencesSlot)) } returns first andThenMany remoteHostWrappers.toList()
        return lbStategy
    }

    private fun mockRetryPolicy(
        first: Boolean,
        vararg outcomes: Boolean,
    ): RetryPolicy {
        val retryPolicy: RetryPolicy = mockk()
        val retryOutcome: RetryPolicy.Outcome = mockk()
        every { retryOutcome.shouldRetry() } returns first andThenMany outcomes.toList()
        val retryOutcomes: List<RetryPolicy.Outcome> = outcomes.map { retryOutcome }.toList()
        every { retryPolicy.evaluate(any(), any(), any()) } returns retryOutcome andThenMany retryOutcomes
        return retryPolicy
    }

    private fun mockRetryPolicy(
        retryContextSlot: CapturingSlot<RetryPolicy.Context>,
        loadBalancerSlot: CapturingSlot<LoadBalancer>,
        lbPreferencesSlot: CapturingSlot<LoadBalancer.Preferences>,
        first: Boolean,
        vararg outcomes: Boolean,
    ): RetryPolicy {
        val retryPolicy: RetryPolicy = mockk()
        val retryOutcome: RetryPolicy.Outcome = mockk()
        every { retryOutcome.shouldRetry() } returns first andThenMany outcomes.toList()
        val retryOutcomes: List<RetryPolicy.Outcome> = outcomes.map { retryOutcome }.toList()
        every {
            retryPolicy.evaluate(capture(retryContextSlot), capture(loadBalancerSlot), capture(lbPreferencesSlot))
        } returns retryOutcome andThenMany retryOutcomes
        return retryPolicy
    }

    private fun mockHostClient(responsePublisher: Publisher<LiveHttpResponse>): ReactorHostHttpClient {
        val hostClient: ReactorHostHttpClient = mockk()
        every { hostClient.sendRequest(any(), any()) } returns responsePublisher
        return hostClient
    }

    private fun backendBuilderWithOrigins(originPort: Int): BackendService.Builder {
        return BackendService.Builder()
            .origins(newOriginBuilder("localhost", originPort).build())
    }

    private fun originWithId(
        host: String,
        appId: String,
        originId: String,
    ): Origin? {
        val hap = HostAndPort.fromString(host)
        return newOriginBuilder(hap.host, hap.port)
            .applicationId(appId)
            .id(originId)
            .build()
    }

    companion object {
        private val SOME_ORIGIN = newOriginBuilder("localhost", 9090).applicationId(GENERIC_APP).build()
        private val SOME_REQ = get("/some-req").build()
        private val DEFAULT_RETRY_POLICY = RetryNTimes(3)

        private val ORIGIN_1 = newOriginBuilder("localhost", 9091).applicationId("app").id("app-01").build()
        private val ORIGIN_2 = newOriginBuilder("localhost", 9092).applicationId("app").id("app-02").build()
        private val ORIGIN_3 = newOriginBuilder("localhost", 9093).applicationId("app").id("app-03").build()
        private val ORIGIN_4 = newOriginBuilder("localhost", 9094).applicationId("app").id("app-04").build()

        private val STICKY_SESSION_CONFIG = StickySessionConfig.stickySessionDisabled()

        private val TEST_REQUEST = get("/test").header(HOST, "www.expedia.com").build()
        private val TEST_RESPONSE = response(OK).build()
        private const val INCOMING_HOSTNAME = "www.expedia.com"
        private const val UPDATED_HOSTNAME = "host.domain.com"
    }
}
