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

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.routing.RoutingContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.configBlock
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Mono
import java.util.Optional
import java.lang.IllegalArgumentException

class ConditionRouterConfigTest : StringSpec({

    val request = LiveHttpRequest.get("/foo").build()

    val routeObjectStore = mockk<StyxObjectStore<RoutingObjectRecord>>()
    every { routeObjectStore.get("secureHandler") } returns
            Optional.of(RoutingObjectRecord(
                    "StaticResponseHandler",
                    mockk(),
                    HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "secure").build()) }))

    every { routeObjectStore.get("fallbackHandler") } returns
            Optional.of(RoutingObjectRecord(
                    "StaticResponseHandler",
                    mockk(),
                    HttpHandler { _, _ -> Eventual.of(response(OK).header("source", "fallback").build()) }))

    val routeHandlerFactory = RoutingObjectFactory(mapOf("StaticResponseHandler" to StaticResponseHandler.Factory()), mockk(), routeObjectStore, mockk(), mockk(), false)

    val context = RoutingContext(
            routeDb = routeObjectStore,
            routingObjectFactory = routeHandlerFactory)
            .get()

    val config = configBlock("""
          config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination:
                      name: proxy-and-log-to-https
                      type: StaticResponseHandler
                      config:
                        status: 200
                        content: "secure"
                fallback:
                  name: proxy-to-http
                  type: StaticResponseHandler
                  config:
                    status: 301
                    content: "insecure"
          """.trimIndent())

    "Builds an instance with fallback handler" {
        val router = ConditionRouter.Factory().build(listOf(), context, config)
        val response = Mono.from(router.handle(request, HttpInterceptorContext(true))).block()

        response.status() shouldBe (OK)
    }

    "Builds condition router instance routes" {
        val router = ConditionRouter.Factory().build(listOf(), context, config)
        val response = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        response.status().code() shouldBe (301)
    }


    "Fallback handler can be specified as a handler reference" {
        val router = ConditionRouter.Factory().build(listOf(), context, configBlock("""
          config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination: secureHandler
                fallback: fallbackHandler
          """.trimIndent()))

        val resp = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        resp.header("source").get() shouldBe ("fallback")
    }

    "Route destination can be specified as a handler reference" {
        val router = ConditionRouter.Factory().build(listOf(), context, configBlock("""
          config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination: secureHandler
                fallback: fallbackHandler
          """.trimIndent())
        )

        val resp = Mono.from(router.handle(request, HttpInterceptorContext(true))).block()
        resp.header("source").get() shouldBe ("secure")
    }


    "Throws exception when routes attribute is missing" {

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.Factory().build(listOf("config", "config"), context, configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  fallback:
                    name: proxy-to-http
                    type: StaticResponseHandler
                    config:
                      status: 301
                      content: "insecure"
            """.trimIndent()))
        }
        e.message shouldBe ("Routing object definition of type 'ConditionRouter', attribute='config.config', is missing a mandatory 'routes' attribute.")
    }

    "Responds with 502 Bad Gateway when fallback attribute is not specified." {

        val router = ConditionRouter.Factory().build(listOf(), context, configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "https"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
        """.trimIndent()))

        val resp = Mono.from(router.handle(request, HttpInterceptorContext())).block()

        resp.status() shouldBe (BAD_GATEWAY)
    }

    "Indicates the condition when fails to compile an DSL expression due to Syntax Error" {

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.Factory().build(listOf("config", "config"), context, configBlock("""
                    config:
                        name: main-router
                        type: ConditionRouter
                        config:
                          routes:
                            - condition: )() == "https"
                              destination:
                                name: proxy-and-log-to-https
                                type: StaticResponseHandler
                                config:
                                  status: 200
                                  content: "secure"
            """.trimIndent()))
        }
        e.message shouldBe ("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition=')() == \"https\"'")
    }

    "Indicates the condition when fails to compile an DSL expression due to unrecognised DSL function name" {

        val e = shouldThrow<IllegalArgumentException> {
            ConditionRouter.Factory().build(listOf("config", "config"), context, configBlock("""
                config:
                    name: main-router
                    type: ConditionRouter
                    config:
                      routes:
                        - condition: nonexistant() == "https"
                          destination:
                            name: proxy-and-log-to-https
                            type: StaticResponseHandler
                            config:
                              status: 200
                              content: "secure"
                """.trimIndent()))
        }
        e.message shouldBe ("Routing object definition of type 'ConditionRouter', attribute='config.config.routes.condition[0]', failed to compile routing expression condition='nonexistant() == \"https\"'")
    }

    "Passes parentage attribute path to the builtins factory" {
        val builtinsFactory = mockk<RoutingObjectFactory> {
            every {build(any(), any())} returns HttpHandler { _, _ -> Eventual.of(response(OK).build()) }
        }

        val context = RoutingContext(routeDb = routeObjectStore, routingObjectFactory = builtinsFactory).get()

        ConditionRouter.Factory().build(listOf("config", "config"), context, configBlock("""
            config:
                name: main-router
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "https"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
                    - condition: path() == "bar"
                      destination:
                        name: proxy-and-log-to-https
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "secure"
                  fallback:
                    name: proxy-and-log-to-https
                    type: StaticResponseHandler
                    config:
                      status: 200
                      content: "secure"
            """.trimIndent()))

        verify {
            builtinsFactory.build(listOf("config", "config", "routes", "destination[0]"), any())
            builtinsFactory.build(listOf("config", "config", "routes", "destination[1]"), any())
            builtinsFactory.build(listOf("config", "config", "fallback"), any())
        }
    }

})
