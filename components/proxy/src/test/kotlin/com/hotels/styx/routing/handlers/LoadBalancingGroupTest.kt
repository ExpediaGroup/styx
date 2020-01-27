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

import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpHeaders
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.configuration.ObjectStore
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.lbGroupTag
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.handle
import com.hotels.styx.requestContext
import com.hotels.styx.routingObjectDef
import io.kotlintest.IsolationMode
import io.kotlintest.eventually
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import io.mockk.verify
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

class LoadBalancingGroupTest : FeatureSpec() {

    private val LOGGER = LoggerFactory.getLogger(LoadBalancingGroupTest::class.java)

    // Tests depend on each other. So run the tests sequentially:
    override fun isolationMode(): IsolationMode = IsolationMode.SingleInstance

    init {
        feature("Load Balancing") {
            val factory = LoadBalancingGroup.Factory()
            val routeDb = StyxObjectStore<RoutingObjectRecord>()
            val headers = HttpHeaders.Builder().build();

            routeDb.insert("appx-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-01", headers)))
            routeDb.insert("appx-02", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-02", headers)))
            routeDb.insert("appx-03", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-03", headers)))

            routeDb.insert("appy-01", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appY")), mockk(), StaticResponseHandler(200, "appy-01", headers)))
            routeDb.insert("appy-02", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appY")), mockk(), StaticResponseHandler(200, "appy-02", headers)))

            routeDb.watch().waitUntil { it.entrySet().size == 5 }

            val lbGroup = factory.build(listOf("appX"), RoutingObjectFactoryContext(objectStore = routeDb).get(), routingObjectDef("""
                type: LoadBalancingGroup
                config:
                  origins: appX
                """.trimIndent())) as LoadBalancingGroup

            scenario("Discovers origins with appropriate tag") {
                val frequencies = mutableMapOf<String, Int>()

                eventually(2.seconds, AssertionError::class.java) {
                    for (i in 1..100) {
                        lbGroup.call(get("/").build())
                                .bodyAs(UTF_8)
                                .let {
                                    val current = frequencies.getOrDefault(it, 0)
                                    frequencies[it] = current + 1
                                }
                    }
                }
                frequencies["appx-01"]!!.shouldBeGreaterThan(15)
                frequencies["appx-02"]!!.shouldBeGreaterThan(15)
                frequencies["appx-03"]!!.shouldBeGreaterThan(15)

                frequencies["appy-01"].shouldBeNull()
                frequencies["appy-02"].shouldBeNull()
            }


            scenario("... and detects new origins") {
                val frequencies = mutableMapOf<String, Int>()

                routeDb.insert("appx-04", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-04", headers)))
                routeDb.insert("appx-05", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-05", headers)))
                routeDb.insert("appx-06", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-06", headers)))

                routeDb.watch().waitUntil { it.entrySet().size == 8 }

                routeDb.remove("appx-01")
                routeDb.remove("appx-02")
                routeDb.remove("appx-03")

                routeDb.watch().waitUntil { it.entrySet().size == 5 }

                for (i in 1..100) {
                    lbGroup.call(get("/").build())
                            .bodyAs(UTF_8)
                            .let {
                                val current = frequencies.getOrDefault(it, 0)
                                frequencies[it] = current + 1
                            }
                }

                frequencies["appx-04"]!!.shouldBeGreaterThan(15)
                frequencies["appx-05"]!!.shouldBeGreaterThan(15)
                frequencies["appx-06"]!!.shouldBeGreaterThan(15)
            }

            scenario("... and detects replaced origins") {
                val frequencies = mutableMapOf<String, Int>()

                routeDb.insert("appx-04", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-04-a", headers)))
                routeDb.insert("appx-05", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-05-b", headers)))
                routeDb.insert("appx-06", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-06-c", headers)))

                routeDb.watch().waitUntil { it["appx-06"].isPresent }

                for (i in 1..100) {
                    lbGroup.call(get("/").build())
                            .bodyAs(UTF_8)
                            .let {
                                val current = frequencies.getOrDefault(it, 0)
                                frequencies[it] = current + 1
                            }
                }

                frequencies["appx-04-a"]!!.shouldBeGreaterThan(15)
                frequencies["appx-05-b"]!!.shouldBeGreaterThan(15)
                frequencies["appx-06-c"]!!.shouldBeGreaterThan(15)
            }

            scenario("... and emits NoAvailableHostsException when load balancing group is empty") {
                routeDb.remove("appx-04")
                routeDb.remove("appx-05")
                routeDb.remove("appx-06")

                routeDb.watch().waitUntil { it.entrySet().size == 2 }

                // The lbGroup is still watching...
                routeDb.watchers().shouldBe(1)

                val e = shouldThrow<NoAvailableHostsException> {
                    lbGroup.call(get("/").build())
                }
                e.message shouldBe "No hosts available for application appX"
            }

            scenario("... and exposes load balancing metric") {
                routeDb.insert("appx-A", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-A", headers)))
                routeDb.insert("appx-B", RoutingObjectRecord.create("HostProxy", setOf(lbGroupTag("appX")), mockk(), StaticResponseHandler(200, "appx-B", headers)))

                routeDb.watch().waitUntil { it.entrySet().size == 4 }

                val invocations = (1..40)
                        .map {
                            lbGroup.handle(get("/").build()).toMono()
                        }

                routeDb.get("appx-A").get().routingObject.metric().ongoingConnections() shouldBe 20
                routeDb.get("appx-B").get().routingObject.metric().ongoingConnections() shouldBe 20

                invocations.forEach {
                    val response = it.block()
                    LOGGER.debug("response: ${response.bodyAs(UTF_8)}")
                }
            }

            scenario("... stop load balancing group") {
                routeDb.entrySet()
                        .map { it.key }
                        .forEach { routeDb.remove(it) }

                lbGroup.stop()
                routeDb.watchers() shouldBe 0
            }
        }

        feature("Lifecycle handling") {
            scenario("Stops watching route database") {
                val watcher = mockk<Disposable>(relaxed = true)
                val lbGroup = LoadBalancingGroup(mockk(), watcher)

                lbGroup.stop()

                verify {
                    watcher.dispose()
                }
            }
        }
    }
}

internal fun Publisher<ObjectStore<RoutingObjectRecord>>.waitUntil(duration: Duration = Duration.ofSeconds(1), predicate: (ObjectStore<RoutingObjectRecord>) -> Boolean) = this
        .toFlux()
        .filter(predicate)
        .blockFirst(duration)


internal fun HttpHandler.call(request: HttpRequest, maxContentBytes: Int = 100000) = this.handle(request.stream(), requestContext())
        .flatMap { it.aggregate(maxContentBytes) }
        .toMono()
        .block()
