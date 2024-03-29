/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.providers

import com.hotels.styx.STATE_ACTIVE
import com.hotels.styx.STATE_INACTIVE
import com.hotels.styx.STATE_UNREACHABLE
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.healthCheckTag
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.ConditionRoutingSpec
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.stateTag
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.newRoutingObject
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.routingObject
import com.hotels.styx.support.wait
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import reactor.kotlin.core.publisher.toMono
import java.lang.Thread.sleep
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class HealthCheckProviderSpec : FeatureSpec() {
    val originsOk = ResourcePaths.fixturesHome(ConditionRoutingSpec::class.java, "/conf/origins/origins-correct.yml")

    val styxServer = StyxServerProvider("""
            proxy:
              connectors:
                http:
                  port: 0

            admin:
              connectors:
                http:
                  port: 0

            services:
              factories:
                backendServiceRegistry:
                  class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                  config: {originsFile: "$originsOk"}

            providers:
              myMonitor:
                type: HealthCheckMonitor
                config:
                  objects: aaa
                  path: /healthCheck/x
                  timeoutMillis: 250
                  intervalMillis: 500
                  healthyThreshold: 3
                  unhealthyThreshold: 2

            httpPipeline:
                type: LoadBalancingGroup
                config:
                  origins: aaa
            """.trimIndent())

    fun hostProxy(tag: String, remote: StyxServerProvider) = """
        type: HostProxy
        tags:
          - $tag
        config:
          host: ${remote().proxyHttpHostHeader()}
        """.trimIndent()

    fun hostProxy(tag1: String, tag2: String, remote: StyxServerProvider) = """
        type: HostProxy
        tags:
          - $tag1
          - $tag2
        config:
          host: ${remote().proxyHttpHostHeader()}
        """.trimIndent()

    init {
        feature("Object monitoring") {
            styxServer.restart()

            styxServer().newRoutingObject("aaa-01", hostProxy(lbGroupTag("aaa"), testServer01)).shouldBe(CREATED)
            styxServer().newRoutingObject("aaa-02", hostProxy(lbGroupTag("aaa"), testServer02)).shouldBe(CREATED)

            scenario("Tags unresponsive origins with state=unreachable tag") {
                pollOrigins(styxServer, "origin-0[12]").let {
                    withClue("Both origins should be taking traffic. Origins distribution: $it") {
                        (it["origin-01"]?:0).shouldBeGreaterThan(15)
                        (it["origin-02"]?:0).shouldBeGreaterThan(15)
                    }
                }

                origin02Active.set(false)

                eventually(2.seconds) {
                    styxServer().routingObject("aaa-02").get().shouldContain(stateTag(STATE_UNREACHABLE))
                }

                eventually(2.seconds) {
                    pollOrigins(styxServer, times = 50).let {
                        withClue("Only the active origin (origin-01) should be taking traffic. Origins distribution: $it") {
                            (it["origin-01"] ?: 0).shouldBe(50)
                        }
                    }
                }
            }

            scenario("Tags responsive origins with state:active TAG") {
                origin02Active.set(true)

                eventually(2.seconds) {
                    styxServer().routingObject("aaa-02").get().shouldContain(stateTag(STATE_ACTIVE))
                }

                eventually(2.seconds) {
                    pollOrigins(styxServer, "origin-0[12]").let {
                        withClue("Both origins should be taking traffic. Origins distribution: $it") {
                            (it["origin-01"] ?: 0).shouldBeGreaterThan(20)
                            (it["origin-02"] ?: 0).shouldBeGreaterThan(20)
                        }
                    }
                }
            }

            scenario("Ignores closed origins") {
                styxServer().newRoutingObject("aaa-04", hostProxy(lbGroupTag("aaa"), stateTag(STATE_INACTIVE), testServer03)).shouldBe(CREATED)
                sleep(5.seconds.inWholeMilliseconds)
                styxServer().routingObject("aaa-04").get().shouldContain(stateTag(STATE_INACTIVE))
                styxServer().routingObject("aaa-04").get().shouldNotContain(healthCheckTag("on" to 0)!!)
            }

            scenario("Detects up new origins") {
                styxServer().newRoutingObject("aaa-03", hostProxy(lbGroupTag("aaa"), testServer03)).shouldBe(CREATED)

                eventually(2.seconds) {
                    styxServer().routingObject("aaa-03").get().shouldContain(stateTag(STATE_ACTIVE))
                    styxServer().routingObject("aaa-03").get().shouldContain(healthCheckTag("on" to 0)!!)
                }

                eventually(2.seconds) {
                    pollOrigins(styxServer, "origin-0[1234]").let {
                        withClue("Both origins should be taking traffic. Origins distribution: $it") {
                            (it["origin-01"] ?: 0).shouldBeGreaterThan(15)
                            (it["origin-02"] ?: 0).shouldBeGreaterThan(15)
                            (it["origin-03"] ?: 0).shouldBeGreaterThan(15)
                        }
                        withClue("Closed origin should be taking no traffic. Origins distribution: $it") {
                            (it["origin-04"] ?: 0).shouldBe(0)
                        }
                    }
                }
            }
        }
    }

    fun pollOrigins(styxServer: StyxServerProvider, responsePattern: String = "origin-0[123]", times: Int = 100): Map<String, Int> {
        val httpClient = StyxHttpClient.Builder().build()
        return List(times) { 1 }
                .groupBy {
                    httpClient
                            .send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                            .wait(debug = false)!!
                            .run {
                                status() shouldBe(OK)
                                bodyAs(UTF_8) shouldMatch responsePattern
                                bodyAs(UTF_8)
                            }
                }.mapValues { (_, value) -> value.sum() }

    }

    class TestEndpoint(val status: HttpResponseStatus = OK, val content: String = "hello world", val active: AtomicBoolean = AtomicBoolean(true)): RoutingObject {
        override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> =
                Eventual(request.aggregate(100000)
                        .toMono()
                        .map {
                            if (active.get()) {
                                response(status)
                                        .body(content, UTF_8)
                                        .build()
                                        .stream()
                            } else {
                                response(INTERNAL_SERVER_ERROR)
                                        .body(content, UTF_8)
                                        .build()
                                        .stream()
                            }
                        })
    }

    val testServerConfig = """
            proxy:
              connectors:
                http:
                  port: 0

            admin:
              connectors:
                http:
                  port: 0

            services:
              factories:
                backendServiceRegistry:
                  class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                  config: {originsFile: "$originsOk"}

            httpPipeline:
              type: PathPrefixRouter
              config:
                routes:
                    - prefix: /
                      destination:
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "origin-0%d"
                    - prefix: /healthCheck/
                      destination:
                        type: TestEndpoint
                        config:
                          pass
            """.trimIndent()

    val origin01Active = AtomicBoolean(true)
    val origin02Active = AtomicBoolean(true)

    val testServer01 = StyxServerProvider(
            testServerConfig.format(1), defaultAdditionalRoutingObjects = mapOf(
                    "TestEndpoint" to RoutingObjectFactory { _, _, _ -> TestEndpoint(content = "origin-01", active = origin01Active) }),
            validateConfig = false)

    val testServer02 = StyxServerProvider(
            testServerConfig.format(2), defaultAdditionalRoutingObjects = mapOf(
                    "TestEndpoint" to RoutingObjectFactory { _, _, _ -> TestEndpoint(content = "origin-02", active = origin02Active) }),
            validateConfig = false)

    val testServer03 = StyxServerProvider(
            testServerConfig.format(3), defaultAdditionalRoutingObjects = mapOf(
                    "TestEndpoint" to RoutingObjectFactory { _, _, _ -> TestEndpoint(content = "origin-03") }),
            validateConfig = false)

    override suspend fun beforeSpec(spec: Spec) {
        testServer01.restart()
        testServer02.restart()
        testServer03.restart()
        styxServer.restart()
    }

    override fun afterSpec(f: suspend (Spec) -> Unit) {
        styxServer.stop()
        testServer01.stop()
        testServer02.stop()
        testServer03.stop()
    }


}
