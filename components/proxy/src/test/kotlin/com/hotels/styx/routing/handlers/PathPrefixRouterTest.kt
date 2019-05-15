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
package com.hotels.styx.routing.handlers

import com.hotels.styx.admin.handlers.toMono
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.common.Pair.pair
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.RoutingObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handle
import com.hotels.styx.routing.routingObjectDef
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional

class PathPrefixRouterTest : FeatureSpec({

    val context = HttpInterceptorContext.create()

    feature("PathPrefixRouter") {
        scenario("No path prefixes configured") {
            val router = PathPrefixRouter(listOf())

            router.route(get("/").build()) shouldBe Optional.empty()
        }

        scenario("Root path") {
            val router = PathPrefixRouter(listOf(pair("/", "root")))

            router.route(get("/").build()) shouldBe Optional.of(RoutingObjectReference("root"))
            router.route(get("/foo/bar").build()) shouldBe Optional.of(RoutingObjectReference("root"))
            router.route(get("/foo/bar/").build()) shouldBe Optional.of(RoutingObjectReference("root"))
            router.route(get("/foo").build()) shouldBe Optional.of(RoutingObjectReference("root"))
        }

        scenario("Choice of most specific path") {
            val router = PathPrefixRouter(listOf(
                    pair("/foo", "foo-file"),
                    pair("/foo/", "foo-path"),
                    pair("/foo/bar", "foo-bar-file"),
                    pair("/foo/bar/", "foo-bar-path"),
                    pair("/foo/baz", "foo-baz-file")
            ))

            router.route(get("/").build()) shouldBe Optional.empty()
            router.route(get("/foo").build()) shouldBe Optional.of(RoutingObjectReference("foo-file"))
            router.route(get("/foo/x").build()) shouldBe Optional.of(RoutingObjectReference("foo-path"))
            router.route(get("/foo/").build()) shouldBe Optional.of(RoutingObjectReference("foo-path"))
            router.route(get("/foo/bar").build()) shouldBe Optional.of(RoutingObjectReference("foo-bar-file"))
            router.route(get("/foo/bar/x").build()) shouldBe Optional.of(RoutingObjectReference("foo-bar-path"))
            router.route(get("/foo/bar/").build()) shouldBe Optional.of(RoutingObjectReference("foo-bar-path"))
            router.route(get("/foo/baz/").build()) shouldBe Optional.of(RoutingObjectReference("foo-baz-file"))
            router.route(get("/foo/baz/y").build()) shouldBe Optional.of(RoutingObjectReference("foo-baz-file"))
        }
    }

    feature("PathPrefixRouterFactory") {
        val objectStore = StyxObjectStore<RoutingObjectRecord>()
        val factory = RoutingObjectFactory(mockk(), objectStore, mockk(), mockk(), false)

        val rootHandler = factory.build(listOf(), routingObjectDef("""
                type: StaticResponseHandler
                config:
                  status: 200
                  content: root
                """.trimIndent()))

        val fooHandler = factory.build(listOf(), routingObjectDef("""
                type: StaticResponseHandler
                config:
                  status: 200
                  content: foo
                """.trimIndent()))

        val barHandler = factory.build(listOf(), routingObjectDef("""
                type: StaticResponseHandler
                config:
                  status: 200
                  content: bar
                """.trimIndent()))

        scenario("") {
            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    routes:
                      - { prefix: /, destination: root }
                      - { prefix: /foo/, destination: foo }
                      - { prefix: /foo/bar, destination: bar }
                """.trimIndent())

            objectStore.insert("root", RoutingObjectRecord("X", routingDef.config(), rootHandler))
            objectStore.insert("foo", RoutingObjectRecord("X", routingDef.config(), fooHandler))
            objectStore.insert("bar", RoutingObjectRecord("X", routingDef.config(), barHandler))

            val factoryContext = HttpHandlerFactory.Context(
                    mockk(),
                    objectStore,
                    mockk(),
                    mockk(),
                    mockk(),
                    false)

            val handler = PathPrefixRouter.Factory().build(listOf(), factoryContext, routingDef);

            handler.handle(HttpRequest.get("/x").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "root"

            handler.handle(HttpRequest.get("/foo").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "root"

            handler.handle(HttpRequest.get("/foo/").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "foo"

            handler.handle(HttpRequest.get("/foo/x").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "foo"

            handler.handle(HttpRequest.get("/foo/bar/").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "bar"

            handler.handle(HttpRequest.get("/foo/bar").build())
                    .toMono()
                    ?.block()
                    ?.bodyAs(UTF_8) shouldBe "bar"
        }
    }
})

