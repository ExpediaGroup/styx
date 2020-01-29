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
package com.hotels.styx.services

import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.STATE_ACTIVE
import com.hotels.styx.STATE_UNREACHABLE
import com.hotels.styx.lbGroupTag
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.routing.config.Builtins.INTERCEPTOR_PIPELINE
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.services.OriginsConfigConverter.Companion.deserialiseOrigins
import com.hotels.styx.services.OriginsConfigConverter.Companion.loadBalancingGroup
import com.hotels.styx.stateTag
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class OriginsConfigConverterTest : StringSpec({
    val serviceDb = StyxObjectStore<ProviderObjectRecord>()

    val ctx = RoutingObjectFactoryContext().get()

    "Translates a BackendService to a LoadBalancingGroup with HostProxy objects" {
        val config = """
            ---
            - id: "app"
              path: "/"
              maxHeaderSize: 1000
              origins:
              - { id: "app1", host: "localhost:9090" }
              - { id: "app2", host: "localhost:9091" }
            """.trimIndent()

        OriginsConfigConverter(serviceDb, ctx, "")
                .routingObjects(deserialiseOrigins(config))
                .let {
                    it.size shouldBe 3

                    it[0].name() shouldBe "app.app1"
                    it[0].tags().shouldContainExactlyInAnyOrder(lbGroupTag("app"), stateTag(STATE_ACTIVE))
                    it[0].type().shouldBe("HostProxy")
                    it[0].config().shouldNotBeNull()
                    it[0].config()["maxHeaderSize"].intValue() shouldBe 1000

                    it[1].name() shouldBe "app.app2"
                    it[1].tags().shouldContainExactlyInAnyOrder(lbGroupTag("app"), stateTag(STATE_ACTIVE))
                    it[1].type().shouldBe("HostProxy")
                    it[1].config().shouldNotBeNull()

                    it[2].name() shouldBe "app"
                    it[2].tags().shouldBeEmpty()
                    it[2].type().shouldBe("LoadBalancingGroup")
                    it[2].config().shouldNotBeNull()
                }
    }

    "Translates one rewrite rules" {
        val config = """
            ---
            - id: "app"
              path: "/"
              rewrites:
              - urlPattern: "/abc/(.*)"
                replacement: "/$1"
              origins:
              - { id: "app1", host: "localhost:9090" }
            """.trimIndent()

        val app = deserialiseOrigins(config)[0]

        loadBalancingGroup(app, null)
                .let {
                    it.name() shouldBe "app"
                    it.type() shouldBe INTERCEPTOR_PIPELINE
                }
    }

    "Translates many rewrite rules" {
        val config = """
            ---
            - id: "app"
              path: "/"
              rewrites:
              - urlPattern: "/abc/(.*)"
                replacement: "/$1"
              - urlPattern: "/def/(.*)"
                replacement: "/$1"
              origins:
              - { id: "app2", host: "localhost:9091" }
            """.trimIndent()

        val app = deserialiseOrigins(config)[0]

        loadBalancingGroup(app, null)
                .let {
                    it.name() shouldBe "app"
                    it.type() shouldBe INTERCEPTOR_PIPELINE
                }
    }

    "Translates a BackendService with TlsSettings to a LoadBalancingGroup with HostProxy objects" {
        val config = """
            ---
            - id: "app"
              path: "/"
              tlsSettings:
                trustAllCerts: true
                sslProvider: JDK
              origins:
              - { id: "app1", host: "localhost:9090" }
              - { id: "app2", host: "localhost:9091" }
            """.trimIndent()

        OriginsConfigConverter(serviceDb, ctx, "")
                .routingObjects(deserialiseOrigins(config))
                .let {
                    it.size shouldBe 3

                    it[0].name() shouldBe "app.app1"
                    it[0].tags().shouldContainExactlyInAnyOrder(lbGroupTag("app"), stateTag(STATE_ACTIVE))
                    it[0].type().shouldBe("HostProxy")
                    it[0].config().shouldNotBeNull()

                    it[1].name() shouldBe "app.app2"
                    it[1].tags().shouldContainExactlyInAnyOrder(lbGroupTag("app"), stateTag(STATE_ACTIVE))
                    it[1].type().shouldBe("HostProxy")
                    it[1].config().shouldNotBeNull()

                    it[2].name() shouldBe "app"
                    it[2].tags().shouldBeEmpty()
                    it[2].type().shouldBe("LoadBalancingGroup")
                    it[2].config().shouldNotBeNull()
                }
    }

    "Translates a list of applications" {
        val config = """
            ---
            - id: "appA"
              path: "/a"
              origins:
              - { id: "appA-1", host: "localhost:9190" }
              - { id: "appA-2", host: "localhost:9191" }
            - id: "appB"
              path: "/b"
              origins:
              - { id: "appB-1", host: "localhost:9290" }
            - id: "appC"
              path: "/c"
              origins:
              - { id: "appC-1", host: "localhost:9290" }
              - { id: "appC-2", host: "localhost:9291" }
            """.trimIndent()

        OriginsConfigConverter(serviceDb, ctx, "")
                .routingObjects(deserialiseOrigins(config))
                .let {
                    it.size shouldBe 8

                    it[0].name() shouldBe "appA.appA-1"
                    it[0].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appA"), stateTag(STATE_ACTIVE))
                    it[0].type().shouldBe("HostProxy")
                    it[0].config().shouldNotBeNull()

                    it[1].name() shouldBe "appA.appA-2"
                    it[1].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appA"), stateTag(STATE_ACTIVE))
                    it[1].type().shouldBe("HostProxy")
                    it[1].config().shouldNotBeNull()

                    it[2].name() shouldBe "appA"
                    it[2].tags().shouldBeEmpty()
                    it[2].type().shouldBe("LoadBalancingGroup")
                    it[2].config().shouldNotBeNull()

                    it[3].name() shouldBe "appB.appB-1"
                    it[3].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appB"), stateTag(STATE_ACTIVE))
                    it[3].type().shouldBe("HostProxy")
                    it[3].config().shouldNotBeNull()

                    it[4].name() shouldBe "appB"
                    it[4].tags().shouldBeEmpty()
                    it[4].type().shouldBe("LoadBalancingGroup")
                    it[4].config().shouldNotBeNull()

                    it[5].name() shouldBe "appC.appC-1"
                    it[5].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appC"), stateTag(STATE_ACTIVE))
                    it[5].type().shouldBe("HostProxy")
                    it[5].config().shouldNotBeNull()

                    it[6].name() shouldBe "appC.appC-2"
                    it[6].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appC"), stateTag(STATE_ACTIVE))
                    it[6].type().shouldBe("HostProxy")
                    it[6].config().shouldNotBeNull()

                    it[7].name() shouldBe "appC"
                    it[7].tags().shouldBeEmpty()
                    it[7].type().shouldBe("LoadBalancingGroup")
                    it[7].config().shouldNotBeNull()
                }
    }

    "Creates HealthCheckObjects from a list of applications" {
        val translator = OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext().get(), "")

        val config = """
            ---
            - id: "appA"
              path: "/a"
              healthCheck:
                uri: "/apphealth.txt"
                intervalMillis: 10000
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appA-1", host: "localhost:9190" }
              - { id: "appA-2", host: "localhost:9191" }
            - id: "appB"
              path: "/b"
              healthCheck:
                uri: "/app-b-health.txt"
                timeoutMillis: 1500
                unhealthyThreshold: 5
                healthyThreshold: 6
              origins:
              - { id: "appB-1", host: "localhost:9290" }
            - id: "appC"
              path: "/c"
              healthCheck:
                uri: "/apphealth.txt"
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appC-1", host: "localhost:9290" }
              - { id: "appC-2", host: "localhost:9291" }
            """.trimIndent()

        val apps = deserialiseOrigins(config)
        val services = translator.healthCheckServices(apps)

        services.size shouldBe 3
        services[0].first shouldBe "appA-monitor"
        services[0].second.type shouldBe "HealthCheckMonitor"
        services[0].second.styxService.shouldNotBeNull()
        services[0].second.config.get(HealthCheckConfiguration::class.java).let {
            it.path shouldBe "/apphealth.txt"
            it.timeoutMillis shouldBe 2000
            it.intervalMillis shouldBe 10000
            it.unhealthyThreshold shouldBe 2
            it.healthyThreshod shouldBe 3
        }

        services[1].first shouldBe "appB-monitor"
        services[1].second.type shouldBe "HealthCheckMonitor"
        services[1].second.styxService.shouldNotBeNull()
        services[1].second.config.get(HealthCheckConfiguration::class.java).let {
            it.path shouldBe "/app-b-health.txt"
            it.timeoutMillis shouldBe 1500
            it.intervalMillis shouldBe 5000
            it.unhealthyThreshold shouldBe 5
            it.healthyThreshod shouldBe 6
        }

        services[2].first shouldBe "appC-monitor"
        services[2].second.type shouldBe "HealthCheckMonitor"
        services[2].second.styxService.shouldNotBeNull()
        services[2].second.config.get(HealthCheckConfiguration::class.java).let {
            it.path shouldBe "/apphealth.txt"
            it.timeoutMillis shouldBe 2000
            it.intervalMillis shouldBe 5000
            it.unhealthyThreshold shouldBe 2
            it.healthyThreshod shouldBe 3
        }

    }

    "Sets inactive tag on an app if there is a valid healthCheck configured" {

        val config = """
            ---
            - id: "appWithHealthCheck"
              path: "/a"
              healthCheck:
                uri: "/apphealth.txt"
                intervalMillis: 10000
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appA-1", host: "localhost:9190" }
            - id: "appMissingHealthCheckUri"
              path: "/b"
              healthCheck:
                intervalMillis: 10000
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appB-1", host: "localhost:9290" }
            - id: "appWithNoHealthCheck"
              path: "/c"
              origins:
              - { id: "appC-1", host: "localhost:9290" }
            """.trimIndent()

        OriginsConfigConverter(serviceDb, RoutingObjectFactoryContext().get(), "")
                .routingObjects(deserialiseOrigins(config))
                .let {
                    it[0].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appWithHealthCheck"), stateTag(STATE_UNREACHABLE))
                    it[2].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appMissingHealthCheckUri"), stateTag(STATE_ACTIVE))
                    it[4].tags().shouldContainExactlyInAnyOrder(lbGroupTag("appWithNoHealthCheck"), stateTag(STATE_ACTIVE))
                }
    }

})

