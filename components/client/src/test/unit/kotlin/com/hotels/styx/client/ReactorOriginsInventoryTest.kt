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

import com.google.common.eventbus.EventBus
import com.hotels.styx.api.Id.GENERIC_APP
import com.hotels.styx.api.Id.id
import com.hotels.styx.api.MeterRegistry
import com.hotels.styx.api.Metrics
import com.hotels.styx.api.MicrometerRegistry
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.OriginsChangeListener
import com.hotels.styx.api.extension.OriginsSnapshot
import com.hotels.styx.client.OriginsInventory.OriginState.ACTIVE
import com.hotels.styx.client.OriginsInventory.OriginState.DISABLED
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.origincommands.DisableOrigin
import com.hotels.styx.client.origincommands.EnableOrigin
import com.hotels.styx.metrics.CentralisedMetrics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.handler.ssl.SslContext
import reactor.netty.resources.LoopResources

class ReactorOriginsInventoryTest : StringSpec() {
    private lateinit var inventory: OriginsInventory
    private lateinit var meterRegistry: MeterRegistry
    private val eventBus: EventBus = mockk(relaxed = true)
    private val monitor: OriginHealthStatusMonitor = mockk(relaxed = true)
    private val connectionPool: ReactorConnectionPool = mockk(relaxed = true)
    private val hostClientFactory: ReactorHostHttpClient.Factory = mockk(relaxed = true)
    private val originStatsFactory: OriginStatsFactory = mockk(relaxed = true)
    private val eventLoopGroup: LoopResources = mockk()
    private val sslContext: SslContext = mockk()

    init {
        beforeTest {
            meterRegistry = MicrometerRegistry(SimpleMeterRegistry())
            inventory =
                ReactorOriginsInventory.Builder(GENERIC_APP)
                    .eventLoopGroup(eventLoopGroup)
                    .eventBus(eventBus)
                    .originHealthMonitor(monitor)
                    .hostClientFactory(hostClientFactory)
                    .metrics(CentralisedMetrics(meterRegistry))
                    .connectionPool(connectionPool)
                    .originStatsFactory(originStatsFactory)
                    .sslContext(sslContext)
                    .build()
        }

        afterTest {
            clearAllMocks()
        }

        "start monitoring new origins" {
            inventory.setOrigins(ORIGIN_1, ORIGIN_2)

            inventory.originCount(ACTIVE) shouldBe 2
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 1.0
            gaugeValue(ORIGIN_2.applicationId().toString(), ORIGIN_2.id().toString()) shouldBe 1.0
            verify(exactly = 1) {
                monitor.monitor(setOf(ORIGIN_1))
                monitor.monitor(setOf(ORIGIN_2))
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "updates on origin port number change" {
            val originV1 =
                newOriginBuilder("acme.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()
            val originV2 =
                newOriginBuilder("acme.com", 443)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()

            inventory.setOrigins(originV1)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue("generic-app", "acme-01") shouldBe 1.0
            verify(exactly = 1) {
                monitor.monitor(setOf(originV1))
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.setOrigins(originV2)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue("generic-app", "acme-01") shouldBe 1.0
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(originV1))
                monitor.monitor(setOf(originV2))
            }
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "updates on origin hostname change" {
            val originV1 =
                newOriginBuilder("acme01.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()
            val originV2 =
                newOriginBuilder("acme02.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()

            inventory.setOrigins(originV1)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue("generic-app", "acme-01") shouldBe 1.0
            verify(exactly = 1) {
                monitor.monitor(setOf(originV1))
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.setOrigins(originV2)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue("generic-app", "acme-01") shouldBe 1.0
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(originV1))
                monitor.monitor(setOf(originV2))
            }
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "stops and restarts monitoring modified origin" {
            val originV1 =
                newOriginBuilder("acme01.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()
            val originV2 =
                newOriginBuilder("acme02.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()

            inventory.setOrigins(originV1)

            verify(exactly = 1) {
                monitor.monitor(setOf(originV1))
            }

            inventory.setOrigins(originV2)

            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(originV1))
                monitor.monitor(setOf(originV2))
            }
        }

        "shuts connection provider on origin change" {
            val originV1 =
                newOriginBuilder("acme01.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()
            val originV2 =
                newOriginBuilder("acme02.com", 80)
                    .applicationId(GENERIC_APP)
                    .id("acme-01")
                    .build()

            val hostClient1: ReactorHostHttpClient = mockk(relaxed = true)
            val hostClient2: ReactorHostHttpClient = mockk(relaxed = true)
            every {
                hostClientFactory.create(
                    originV1, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient1
            every {
                hostClientFactory.create(
                    originV2, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient2

            inventory.setOrigins(originV1)

            verify(exactly = 1) {
                hostClientFactory.create(
                    originV1, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            }

            inventory.setOrigins(originV2)

            verify(exactly = 1) {
                hostClientFactory.create(
                    originV2, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
                hostClient1.close()
            }
        }

        "ignores unchanged origins" {
            inventory.setOrigins(ORIGIN_1, ORIGIN_2)

            inventory.originCount(ACTIVE) shouldBe 2
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 1.0
            gaugeValue(ORIGIN_2.applicationId().toString(), ORIGIN_2.id().toString()) shouldBe 1.0
            verify(exactly = 1) {
                monitor.monitor(setOf(ORIGIN_1))
                monitor.monitor(setOf(ORIGIN_2))
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.setOrigins(ORIGIN_1, ORIGIN_2)
            inventory.originCount(ACTIVE) shouldBe 2
            verify(exactly = 1) {
                monitor.monitor(setOf(ORIGIN_1))
                monitor.monitor(setOf(ORIGIN_2))
                eventBus.post(any<OriginsSnapshot>())
            }
            verify(exactly = 0) {
                monitor.stopMonitoring(setOf(ORIGIN_1))
                monitor.stopMonitoring(setOf(ORIGIN_2))
            }
        }

        "stop monitoring on origins removal" {
            inventory.setOrigins(ORIGIN_1, ORIGIN_2)

            inventory.originCount(ACTIVE) shouldBe 2
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 1.0
            gaugeValue(ORIGIN_2.applicationId().toString(), ORIGIN_2.id().toString()) shouldBe 1.0
            verify(exactly = 1) {
                monitor.monitor(setOf(ORIGIN_1))
                monitor.monitor(setOf(ORIGIN_2))
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.setOrigins(ORIGIN_2)
            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe null
            gaugeValue(ORIGIN_2.applicationId().toString(), ORIGIN_2.id().toString()) shouldBe 1.0
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(ORIGIN_1))
            }
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "shuts connection provider on origin removal" {
            val hostClient1: ReactorHostHttpClient = mockk(relaxed = true)
            val hostClient2: ReactorHostHttpClient = mockk(relaxed = true)
            every {
                hostClientFactory.create(
                    ORIGIN_1, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient1
            every {
                hostClientFactory.create(
                    ORIGIN_2, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient2

            inventory.setOrigins(ORIGIN_1, ORIGIN_2)

            verify(exactly = 2) {
                hostClientFactory.create(
                    any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            }

            inventory.setOrigins(ORIGIN_2)

            verify(exactly = 1) {
                hostClient1.close()
            }
        }

        "does not disable origins not belonging to the app" {
            inventory.setOrigins(ORIGIN_1)

            verify(exactly = 1) {
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.onCommand(DisableOrigin(id("some-other-app"), ORIGIN_1.id()))

            inventory.originCount(ACTIVE) shouldBe 1
            verify(exactly = 1) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "does not enable origins not belonging to the app" {
            inventory.setOrigins(ORIGIN_1)

            verify(exactly = 1) {
                eventBus.post(any<OriginsSnapshot>())
            }

            inventory.onCommand(DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()))
            inventory.onCommand(EnableOrigin(id("some-other-app"), ORIGIN_1.id()))

            inventory.originCount(ACTIVE) shouldBe 0
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "removes from active set and stops health check monitoring on disabling an origin" {
            inventory.setOrigins(ORIGIN_1)

            inventory.onCommand(DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()))

            inventory.originCount(ACTIVE) shouldBe 0
            inventory.originCount(DISABLED) shouldBe 1

            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe -1.0
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(ORIGIN_1))
            }
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "removes from inactive set and stops health check monitoring on disabling an origin" {
            inventory.setOrigins(ORIGIN_1)

            inventory.originUnhealthy(ORIGIN_1)
            inventory.onCommand(DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()))

            inventory.originCount(ACTIVE) shouldBe 0
            inventory.originCount(DISABLED) shouldBe 1

            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe -1.0
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(ORIGIN_1))
            }
            verify(exactly = 3) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "re-initiates health check monitoring on enabling an origin" {
            inventory.setOrigins(ORIGIN_1)

            inventory.onCommand(DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()))
            inventory.onCommand(EnableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()))

            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 0.0
            verify(exactly = 2) {
                monitor.monitor(setOf(ORIGIN_1))
            }
            verify(exactly = 3) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "updates on removing unhealthy origins from active set" {
            inventory.setOrigins(ORIGIN_1)
            inventory.originCount(ACTIVE) shouldBe 1

            inventory.originUnhealthy(ORIGIN_1)

            inventory.originCount(ACTIVE) shouldBe 0
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 0.0
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "updates on adding healthy origins back to active set" {
            inventory.setOrigins(ORIGIN_1)
            inventory.originCount(ACTIVE) shouldBe 1

            inventory.originUnhealthy(ORIGIN_1)
            inventory.originHealthy(ORIGIN_1)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 1.0
            verify(exactly = 3) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "repeatedly reporting healthy does not affect current active origins" {
            inventory.setOrigins(ORIGIN_1)
            inventory.originCount(ACTIVE) shouldBe 1

            inventory.originHealthy(ORIGIN_1)
            inventory.originHealthy(ORIGIN_1)
            inventory.originHealthy(ORIGIN_1)

            inventory.originCount(ACTIVE) shouldBe 1
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 1.0
            verify(exactly = 1) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "repeatedly reporting unhealthy does not affect current active origins" {
            inventory.setOrigins(ORIGIN_1)
            inventory.originCount(ACTIVE) shouldBe 1

            inventory.originUnhealthy(ORIGIN_1)
            inventory.originUnhealthy(ORIGIN_1)
            inventory.originUnhealthy(ORIGIN_1)

            inventory.originCount(ACTIVE) shouldBe 0
            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe 0.0
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
        }

        "announces listeners on origin state change" {
            val listener: OriginsChangeListener = mockk()

            inventory.addOriginsChangeListener(listener)
            inventory.setOrigins(ORIGIN_1)
            inventory.originUnhealthy(ORIGIN_1)

            verify(exactly = 2) {
                listener.originsChanged(any<OriginsSnapshot>())
            }
        }

        "registers to event bus when created" {
            verify(exactly = 1) {
                eventBus.register(inventory)
            }
        }

        "stops monitoring and unregisters when closed" {
            val hostClient1: ReactorHostHttpClient = mockk(relaxed = true)
            val hostClient2: ReactorHostHttpClient = mockk(relaxed = true)
            every {
                hostClientFactory.create(
                    ORIGIN_1, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient1
            every {
                hostClientFactory.create(
                    ORIGIN_2, any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                )
            } returns hostClient2

            inventory.setOrigins(ORIGIN_1, ORIGIN_2)

            inventory.close()

            gaugeValue(ORIGIN_1.applicationId().toString(), ORIGIN_1.id().toString()) shouldBe null
            gaugeValue(ORIGIN_2.applicationId().toString(), ORIGIN_2.id().toString()) shouldBe null
            verify(exactly = 2) {
                eventBus.post(any<OriginsSnapshot>())
            }
            verify(exactly = 1) {
                monitor.stopMonitoring(setOf(ORIGIN_1))
                monitor.stopMonitoring(setOf(ORIGIN_2))
                hostClient1.close()
                hostClient2.close()
                eventBus.unregister(inventory)
            }
        }
    }

    private fun gaugeValue(
        appId: String,
        originId: String,
    ): Double? {
        val name = "proxy.client.originHealthStatus"
        val tags = Tags.of(Metrics.APPID_TAG, appId, Metrics.ORIGINID_TAG, originId)
        return gauge(name, tags)?.value()
    }

    private fun gauge(
        name: String,
        tags: Tags,
    ): Gauge? = meterRegistry.find(name).tags(tags).gauge()

    companion object {
        private val ORIGIN_1 =
            newOriginBuilder("localhost", 8001)
                .applicationId(GENERIC_APP)
                .id("app-01")
                .build()
        private val ORIGIN_2 =
            newOriginBuilder("localhost", 8002)
                .applicationId(GENERIC_APP)
                .id("app-02")
                .build()
    }
}
