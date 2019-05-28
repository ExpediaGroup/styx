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
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.routing.RoutingContext
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.handle
import com.hotels.styx.routing.mockObject
import com.hotels.styx.routing.mockObjectFactory
import com.hotels.styx.routing.ref
import com.hotels.styx.routing.routeLookup
import com.hotels.styx.routing.routingObjectDef
import com.hotels.styx.routing.routingObjectFactory
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono


class ConditionRouterConfigTest : FeatureSpec({

    val request = LiveHttpRequest.get("/foo").build()

    val context = RoutingContext(
            factory = routingObjectFactory(routeLookup {
                ref("secureHandler" to RoutingObject { _, _ -> Eventual.of(response(OK).header("source", "secure").build()) })
                ref("fallbackHandler" to RoutingObject { _, _ -> Eventual.of(response(OK).header("source", "fallback").build()) })
            }))

    val config = routingObjectDef("""
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



    feature("Factory") {
        scenario("Builds an instance with fallback handler") {
            val router = ConditionRouter.Factory().build(listOf(), context.get(), config)
            val response = router.handle(request, HttpInterceptorContext(true)).toMono().block()

            response?.status() shouldBe (OK)
        }

        scenario("Builds condition router instance routes") {
            val router = ConditionRouter.Factory().build(listOf(), context.get(), config)
            val response = router.handle(request, HttpInterceptorContext()).toMono().block()

            response?.status()?.code() shouldBe (301)
        }


        scenario("Fallback handler can be specified as a handler reference") {
            val router = ConditionRouter.Factory().build(listOf(), context.get(), routingObjectDef("""
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination: secureHandler
                fallback: fallbackHandler
            """.trimIndent()))

            val response = router.handle(request, HttpInterceptorContext()).toMono().block()

            response?.header("source")?.get() shouldBe ("fallback")
        }

        scenario("Route destination can be specified as a handler reference") {
            val router = ConditionRouter.Factory().build(listOf(), context.get(), routingObjectDef("""
              name: main-router
              type: ConditionRouter
              config:
                routes:
                  - condition: protocol() == "https"
                    destination: secureHandler
                fallback: fallbackHandler
                """.trimIndent())
            )

            val response = router.handle(request, HttpInterceptorContext(true)).toMono().block()
            response?.header("source")?.get() shouldBe ("secure")
        }


        scenario("Throws exception when routes attribute is missing") {
            val e = shouldThrow<IllegalArgumentException> {
                ConditionRouter.Factory().build(listOf("config", "config"), context.get(), routingObjectDef("""
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

        scenario("Indicates the condition when fails to compile an DSL expression due to Syntax Error") {
            val e = shouldThrow<IllegalArgumentException> {
                ConditionRouter.Factory().build(listOf("config", "config"), context.get(), routingObjectDef("""
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

        scenario("Indicates the condition when fails to compile an DSL expression due to unrecognised DSL function name") {
            val e = shouldThrow<IllegalArgumentException> {
                ConditionRouter.Factory().build(listOf("config", "config"), context.get(), routingObjectDef("""
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

        scenario("Passes parentage attribute path to the builtins factory") {
            val builtinsFactory = mockk<RoutingObjectFactory> {
                every { build(any(), any()) } returns mockObject()
            }

            val context = RoutingContext(factory = builtinsFactory)

            ConditionRouter.Factory().build(listOf("config", "config"), context.get(), routingObjectDef("""
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
    }


    feature("Routing") {

        scenario("Responds with 502 Bad Gateway when fallback attribute is not specified.") {
            val router = ConditionRouter.Factory().build(listOf(), context.get(), routingObjectDef("""
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

            val response = router.handle(request, HttpInterceptorContext()).toMono().block()

            response?.status() shouldBe (BAD_GATEWAY)
        }
    }

    feature("Lifecycle management") {
        scenario("Calls stop() on inlined fallback handler") {
            val mockObject: RoutingObject = mockObject()

            val context = RoutingContext(
                    factory = routingObjectFactory(
                            builtins = mapOf("TestObject" to mockObjectFactory(listOf(mockObject)))))

            val router = ConditionRouter.Factory().build(listOf(), context.get(), routingObjectDef("""
                type: ConditionRouter
                config:
                  routes: []
                  fallback:
                    type: TestObject
                    config:
                      nothing: here
                """.trimIndent()))

            router.stop()

            verify { mockObject.stop() }
        }

        scenario("Does not call stop() on fallback handler reference") {
            val mockObject = mockObject()

            val context = RoutingContext(
                    factory = routingObjectFactory(
                            routeLookup {
                                ref("fallbackHandler" to mockObject)
                            }))

            val router = ConditionRouter.Factory().build(listOf(), context.get(), routingObjectDef("""
                type: ConditionRouter
                config:
                  routes: []
                  fallback: fallbackHandler
            """.trimIndent()))

            router.stop()

            router.handle(HttpRequest.get("/").build())

            verify(exactly = 1) { mockObject.handle(any(), any()) }
            verify(exactly = 0) { mockObject.stop() }
        }

        scenario("Calls stop() on inlined destinations") {
            val instance1: RoutingObject = mockObject()
            val instance2: RoutingObject = mockObject()

            val mockObjectFactory = mockk<HttpHandlerFactory> {
                every { build(any(), any(), any()) } returnsMany listOf(instance1, instance2)
            }

            val context = RoutingContext(
                    factory = routingObjectFactory(
                            builtins = mapOf("TestObject" to mockObjectFactory(listOf(instance1, instance2)))))
                    .get()

            val router = ConditionRouter.Factory().build(listOf(), context, routingObjectDef("""
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "http"
                      destination:
                        type: TestObject
                        config:
                          nothing: here
                    - condition: protocol() == "https"
                      destination:
                        type: TestObject
                        config:
                          nothing: here
            """.trimIndent()))

            router.stop()

            verify { instance1.stop() }
            verify { instance2.stop() }
        }

        scenario("Does not call stop() on destination references") {
            val mockObject1: RoutingObject = mockObject()
            val mockObject2: RoutingObject = mockObject()

            val context = RoutingContext(
                    factory = routingObjectFactory(
                            routeLookup {
                                ref("ref1" to mockObject1)
                                ref("ref2" to mockObject2)
                            }))
                    .get()

            val router = ConditionRouter.Factory().build(listOf(), context, routingObjectDef("""
                type: ConditionRouter
                config:
                  routes:
                    - condition: protocol() == "http"
                      destination: ref1
                    - condition: protocol() == "https"
                      destination: ref2
            """.trimIndent()))

            router.stop()

            verify(exactly = 0) { mockObject1.stop() }
            verify(exactly = 0) { mockObject2.stop() }
        }
    }
})
