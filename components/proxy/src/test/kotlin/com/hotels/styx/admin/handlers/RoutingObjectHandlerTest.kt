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
package com.hotels.styx.admin.handlers

import com.fasterxml.jackson.databind.node.ObjectNode
import com.hotels.styx.api.HttpRequest.delete
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpRequest.put
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.routing.RoutingObjectAdapter
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handle
import com.hotels.styx.routing.mockObject
import io.kotlintest.matchers.types.shouldBeTypeOf
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class RoutingObjectHandlerTest : FeatureSpec({

    val routeDatabase = StyxObjectStore<RoutingObjectRecord>()

    val objectFactory = RoutingObjectFactory(routeDatabase)

    val staticResponseObject = """
                            type: "StaticResponseHandler"
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent()

    feature("Route database management") {
        scenario("Injecting new objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body(staticResponseObject, UTF_8)
                            .build())
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            routeDatabase.get("staticResponse").isPresent shouldBe true
            routeDatabase.get("staticResponse").get().type shouldBe "StaticResponseHandler"
            routeDatabase.get("staticResponse").get().config.shouldBeTypeOf<ObjectNode>()
            routeDatabase.get("staticResponse").get().routingObject.shouldBeTypeOf<RoutingObjectAdapter>()
        }

        scenario("Retrieving objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body(staticResponseObject, UTF_8)
                            .build())
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            val response = handler.handle(get("/admin/routing/objects/staticResponse").build())
                    .toMono()
                    .block()

            response!!.status() shouldBe OK
            response.bodyAs(UTF_8).trim() shouldBe """
                            ---
                            name: "staticResponse"
                            type: "StaticResponseHandler"
                            tags: []
                            config:
                              status: 200
                              content: "Hello, world!"
                              """
                    .trimIndent()
                    .trim()
        }

        scenario("Fetching all routing objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body(staticResponseObject, UTF_8)
                            .build())
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            val r = handler.handle(
                    put("/admin/routing/objects/conditionRouter")
                            .body("""
                                type: ConditionRouter
                                config:
                                   routes:
                                     - condition: path() == "/bar"
                                       destination: b
                                   fallback: fb
                                """.trimIndent(), UTF_8)
                            .build())
                    .toMono()
                    .block()

            println(r?.bodyAs(UTF_8))
            r?.status() shouldBe CREATED


            val response = handler.handle(get("/admin/routing/objects").build())
                    .toMono()
                    .block()

            response?.status() shouldBe OK

            response?.bodyAs(UTF_8)?.trim() shouldBe """
                ---
                name: "conditionRouter"
                type: "ConditionRouter"
                tags: []
                config:
                  routes:
                  - condition: "path() == \"/bar\""
                    destination: "b"
                  fallback: "fb"

                ---
                name: "staticResponse"
                type: "StaticResponseHandler"
                tags: []
                config:
                  status: 200
                  content: "Hello, world!"
            """.trimIndent().trim()
        }

        scenario("Replacing existing objects triggers lifecycle methods") {
            val db = StyxObjectStore<RoutingObjectRecord>()
            val mockObject = RoutingObjectAdapter(mockObject())

            db.insert("staticResponse", RoutingObjectRecord("StaticResponseHandler", mockk(), mockk(), mockObject))
            db.get("staticResponse").isPresent shouldBe true

            val handler = RoutingObjectHandler(db, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                                type: StaticResponseHandler
                                config:
                                   status: 200
                                   content: "Hey man!"
                                """.trimIndent(), UTF_8)
                            .build())
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            db.get("staticResponse").isPresent shouldBe true
            db.get("staticResponse").get().type shouldBe "StaticResponseHandler"

            verify(exactly = 1) { mockObject.stop() }
        }

        scenario("Removing existing objects triggers lifecycle methods") {
            val db = StyxObjectStore<RoutingObjectRecord>()
            val mockObject = RoutingObjectAdapter(mockObject())

            db.insert("staticResponse", RoutingObjectRecord("StaticResponseHandler", mockk(), mockk(), mockObject))

            val handler = RoutingObjectHandler(db, objectFactory)

            handler.handle(
                    delete("/admin/routing/objects/staticResponse").build())
                    .toMono()
                    .block()
                    ?.status() shouldBe OK

            db.get("staticResponse").isPresent shouldBe false

            verify(exactly = 1) { mockObject.stop() }
        }

        scenario("Removing a non-existent object") {
            val db = StyxObjectStore<RoutingObjectRecord>()

            val handler = RoutingObjectHandler(db, objectFactory)

            handler.handle(
                    delete("/admin/routing/objects/staticResponse").build())
                    .toMono()
                    .block()
                    ?.status() shouldBe NOT_FOUND
        }

    }
})
