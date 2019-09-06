/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.hotels.styx.routing.RoutingObjectFactoryContext
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.services.OriginsConfigConverter.Companion.OBJECT_CREATOR_TAG
import com.hotels.styx.services.OriginsConfigConverter.Companion.ROOT_OBJECT_NAME
import com.hotels.styx.services.OriginsConfigConverter.Companion.deserialiseOrigins
import io.kotlintest.matchers.collections.shouldContainAll
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
              origins:
              - { id: "app1", host: "localhost:9090" }
              - { id: "app2", host: "localhost:9091" }
            """.trimIndent()

        OriginsConfigConverter(serviceDb, ctx, "")
                .routingObjects(deserialiseOrigins(config))
                .let {
                    it.size shouldBe 4

                    it[0].first shouldBe "app.app1"
                    it[0].second.tags.shouldContainAll("app", "source=OriginsFileConverter")
                    it[0].second.type.shouldBe("HostProxy")
                    it[0].second.routingObject.shouldNotBeNull()

                    it[1].first shouldBe "app.app2"
                    it[1].second.tags.shouldContainAll("app", "source=OriginsFileConverter")
                    it[1].second.type.shouldBe("HostProxy")
                    it[1].second.routingObject.shouldNotBeNull()

                    it[2].first shouldBe "app"
                    it[2].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[2].second.type.shouldBe("LoadBalancingGroup")
                    it[2].second.routingObject.shouldNotBeNull()

                    it[3].first shouldBe ROOT_OBJECT_NAME
                    it[3].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[3].second.type.shouldBe("PathPrefixRouter")
                    it[3].second.routingObject.shouldNotBeNull()
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
                    it.size shouldBe 4

                    it[0].first shouldBe "app.app1"
                    it[0].second.tags.shouldContainAll("app", "source=OriginsFileConverter")
                    it[0].second.type.shouldBe("HostProxy")
                    it[0].second.routingObject.shouldNotBeNull()

                    it[1].first shouldBe "app.app2"
                    it[1].second.tags.shouldContainAll("app", "source=OriginsFileConverter")
                    it[1].second.type.shouldBe("HostProxy")
                    it[1].second.routingObject.shouldNotBeNull()

                    it[2].first shouldBe "app"
                    it[2].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[2].second.type.shouldBe("LoadBalancingGroup")
                    it[2].second.routingObject.shouldNotBeNull()

                    it[3].first shouldBe ROOT_OBJECT_NAME
                    it[3].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[3].second.type.shouldBe("PathPrefixRouter")
                    it[3].second.routingObject.shouldNotBeNull()
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
                    it.size shouldBe 9

                    it[0].first shouldBe "appA.appA-1"
                    it[0].second.tags.shouldContainAll("appA", "source=OriginsFileConverter")
                    it[0].second.type.shouldBe("HostProxy")
                    it[0].second.routingObject.shouldNotBeNull()

                    it[1].first shouldBe "appA.appA-2"
                    it[1].second.tags.shouldContainAll("appA", "source=OriginsFileConverter")
                    it[1].second.type.shouldBe("HostProxy")
                    it[1].second.routingObject.shouldNotBeNull()

                    it[2].first shouldBe "appA"
                    it[2].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[2].second.type.shouldBe("LoadBalancingGroup")
                    it[2].second.routingObject.shouldNotBeNull()

                    it[3].first shouldBe "appB.appB-1"
                    it[3].second.tags.shouldContainAll("appB", "source=OriginsFileConverter")
                    it[3].second.type.shouldBe("HostProxy")
                    it[3].second.routingObject.shouldNotBeNull()

                    it[4].first shouldBe "appB"
                    it[4].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[4].second.type.shouldBe("LoadBalancingGroup")
                    it[4].second.routingObject.shouldNotBeNull()

                    it[5].first shouldBe "appC.appC-1"
                    it[5].second.tags.shouldContainAll("appC", "source=OriginsFileConverter")
                    it[5].second.type.shouldBe("HostProxy")
                    it[5].second.routingObject.shouldNotBeNull()

                    it[6].first shouldBe "appC.appC-2"
                    it[6].second.tags.shouldContainAll("appC", "source=OriginsFileConverter")
                    it[6].second.type.shouldBe("HostProxy")
                    it[6].second.routingObject.shouldNotBeNull()

                    it[7].first shouldBe "appC"
                    it[7].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[7].second.type.shouldBe("LoadBalancingGroup")
                    it[7].second.routingObject.shouldNotBeNull()

                    it[8].first shouldBe "pathPrefixRouter"
                    it[8].second.tags.shouldContainAll("source=OriginsFileConverter")
                    it[8].second.type.shouldBe("PathPrefixRouter")
                    it[8].second.routingObject.shouldNotBeNull()
                }
    }

    "HealthCheckTranslator converts a list of applications to HealthCheckObjects" {
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
                uri: "/apphealth.txt"
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appB-1", host: "localhost:9290" }
            - id: "appC"
              path: "/c"
              healthCheck:
                uri: "/apphealth.txt"
                intervalMillis: 10000
                unhealthyThreshold: 2
                healthyThreshold: 3
              origins:
              - { id: "appC-1", host: "localhost:9290" }
              - { id: "appC-2", host: "localhost:9291" }
            """.trimIndent()

        val services = translator.healthCheckServices(deserialiseOrigins(config))

        services.size shouldBe 3
        services[0].first shouldBe "appA"
        services[0].second.tags.shouldContainAll(OBJECT_CREATOR_TAG)
        services[0].second.type shouldBe "HealthCheckMonitor"
        services[0].second.styxService.shouldNotBeNull()

        services[1].first shouldBe "appB"
        services[1].second.tags.shouldContainAll(OBJECT_CREATOR_TAG)
        services[1].second.type shouldBe "HealthCheckMonitor"
        services[1].second.styxService.shouldNotBeNull()

        services[2].first shouldBe "appC"
        services[2].second.tags.shouldContainAll(OBJECT_CREATOR_TAG)
        services[2].second.type shouldBe "HealthCheckMonitor"
        services[2].second.styxService.shouldNotBeNull()
    }

})

