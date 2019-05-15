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
import com.hotels.styx.Environment
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpRequest.delete
import com.hotels.styx.api.HttpRequest.put
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.StaticResponseHandler
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.matchers.types.shouldBeTypeOf
import io.kotlintest.matchers.types.shouldNotBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class RoutingObjectHandlerTest : FeatureSpec({

    val environment = Environment.Builder().build()

    val routeDatabase = StyxObjectStore<RoutingObjectRecord>()

    val objectFactory = RoutingObjectFactory(environment, routeDatabase, listOf(), mockk(), true)

    feature("Route database management") {
        scenario("Injecting new objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                            name: "staticResponse"
                            type: "StaticResponseHandler"
                            tags: []
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            routeDatabase.get("staticResponse").isPresent shouldBe true
            routeDatabase.get("staticResponse").get().type shouldBe "StaticResponseHandler"
            routeDatabase.get("staticResponse").get().config.shouldBeTypeOf<ObjectNode>()
            routeDatabase.get("staticResponse").get().handler.shouldBeTypeOf<StaticResponseHandler>()
        }

        scenario("Retrieving objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                            type: StaticResponseHandler
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            val response = handler.handle(HttpRequest.get("/admin/routing/objects/staticResponse").build().stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
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
                            .body("""
                            type: StaticResponseHandler
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
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
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()

            println(r?.bodyAs(UTF_8))
            r?.status() shouldBe CREATED


            val response = handler.handle(
                    HttpRequest.get("/admin/routing/objects").build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
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

        scenario("Replacing existing objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                            type: StaticResponseHandler
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            routeDatabase.get("staticResponse").isPresent shouldBe true
            val previousConfig = routeDatabase.get("staticResponse").get().config
            val previousHandler = routeDatabase.get("staticResponse").get().handler

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                            type: StaticResponseHandler
                            config:
                               status: 200
                               content: "Hey man!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            routeDatabase.get("staticResponse").isPresent shouldBe true
            routeDatabase.get("staticResponse").get().type shouldBe "StaticResponseHandler"
            routeDatabase.get("staticResponse").get().config shouldNotBeSameInstanceAs previousConfig
            routeDatabase.get("staticResponse").get().handler shouldNotBeSameInstanceAs previousHandler
        }

        scenario("Removing existing objects") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    put("/admin/routing/objects/staticResponse")
                            .body("""
                            type: StaticResponseHandler
                            config:
                               status: 200
                               content: "Hello, world!"
                            """.trimIndent(),
                                    UTF_8)
                            .build()
                            .stream(), HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe CREATED

            routeDatabase.get("staticResponse").isPresent shouldBe true

            handler.handle(
                    delete("/admin/routing/objects/staticResponse").build().stream(),
                    HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe OK

            routeDatabase.get("staticResponse").isPresent shouldBe false
        }

        scenario("Removing a non-existent object") {
            val handler = RoutingObjectHandler(routeDatabase, objectFactory)

            handler.handle(
                    delete("/admin/routing/objects/staticResponse").build().stream(),
                    HttpInterceptorContext.create())
                    .flatMap { it.aggregate(2000) }
                    .toMono()
                    .block()
                    ?.status() shouldBe NOT_FOUND
        }

    }
})
