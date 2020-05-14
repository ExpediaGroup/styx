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

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.config.schema.SchemaValidationException
import com.hotels.styx.handle
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.mockObject
import com.hotels.styx.ref
import com.hotels.styx.routeLookup
import com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_SCHEMAS
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routingObjectDef
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FeatureSpec
import io.mockk.verify
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets.UTF_8

class PathPrefixRouterFactoryTest : FeatureSpec({

    val context = RoutingObjectFactoryContext()

    feature("PathPrefixRouterFactory") {

        scenario("Builds a PathPrefixRouter instance") {
            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    routes:
                      - { prefix: /, destination: root }
                      - { prefix: /foo/, destination: foo }
                    """.trimIndent())

            val context = RoutingObjectFactoryContext(
                    routeRefLookup = routeLookup {
                        ref("root" to mockObject("root"))
                        ref("foo" to mockObject("foo"))
                    }
            )

            val handler = PathPrefixRouter.Factory().build(listOf(), context.get(), routingDef);

            handler.handle(HttpRequest.get("/x").build())
                    .toMono()
                    .block()!!.bodyAs(UTF_8) shouldBe "root"

            handler.handle(HttpRequest.get("/foo/").build())
                    .toMono()
                    .block()!!.bodyAs(UTF_8) shouldBe "foo"
        }

        scenario("Supports inline routing object definitions") {
            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    routes:
                      - prefix: /foo
                        destination:
                          type: StaticResponseHandler
                          config:
                             status: 200
                             content: hello
                """.trimIndent())

            val handler = PathPrefixRouter.Factory().build(listOf(), context.get(), routingDef);

            handler.handle(HttpRequest.get("/foo").build())
                    .toMono()
                    .block()!!.bodyAs(UTF_8) shouldBe "hello"
        }

        scenario("Missing routes attribute") {
            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    bar: 1
                """.trimIndent())

            val e = shouldThrow<IllegalArgumentException> {
                PathPrefixRouter.Factory().build(listOf(), context.get(), routingDef);
            }

            e.message shouldBe "Routing object definition of type 'PathPrefixRouter', attribute='', is missing a mandatory 'routes' attribute."
        }
    }

    feature("Schema validation") {
        val EXTENSIONS = { key: String -> BUILTIN_HANDLER_SCHEMAS[key] }

        scenario("Accepts inlined routing object definitions") {
            val jsonNode = YamlConfig("""
                    routes:
                      - prefix: /foo
                        destination:
                          type: StaticResponseHandler
                          config:
                             status: 200
                             content: hello
                      - prefix: /bar
                        destination:
                          type: StaticResponseHandler
                          config:
                             status: 200
                             content: hello
                """.trimIndent()).`as`(JsonNode::class.java)

            PathPrefixRouter.SCHEMA.validate(listOf(), jsonNode, jsonNode, EXTENSIONS)
        }

        scenario("Accepts named routing object references") {
            val jsonNode = YamlConfig("""
                    routes:
                      - prefix: /foo
                        destination: foo
                      - prefix: /bar
                        destination: bar
                """.trimIndent()).`as`(JsonNode::class.java)

            PathPrefixRouter.SCHEMA.validate(listOf(), jsonNode, jsonNode, EXTENSIONS)
        }

        scenario("Accepts a mix of routing object references and inline definitions") {
            val jsonNode = YamlConfig("""
                    routes:
                      - prefix: /foo
                        destination: bar
                      - prefix: /bar
                        destination:
                          type: StaticResponseHandler
                          config:
                             status: 200
                             content: hello
                """.trimIndent()).`as`(JsonNode::class.java)

            PathPrefixRouter.SCHEMA.validate(listOf(), jsonNode, jsonNode, EXTENSIONS)
        }

        scenario("Accepts an empty routes list") {
            val jsonNode = YamlConfig("""
                    routes: []
                """.trimIndent()).`as`(JsonNode::class.java)

            PathPrefixRouter.SCHEMA.validate(listOf(), jsonNode, jsonNode, EXTENSIONS)
        }

        scenario("Rejects unrelated attributes") {
            val jsonNode = YamlConfig("""
                    routes: []
                    notAllowed: here
                """.trimIndent()).`as`(JsonNode::class.java)

            val e = shouldThrow<SchemaValidationException> {
                PathPrefixRouter.SCHEMA.validate(listOf(), jsonNode, jsonNode, EXTENSIONS)
            }

            e.message shouldBe "Unexpected field: 'notAllowed'"
        }
    }

    feature("Lifecycle management") {
        scenario("Calls stop() for inlined handlers") {
            val child1 = mockObject()
            val child2 = mockObject()

            val context = RoutingObjectFactoryContext(
                    objectFactories = mapOf(
                            "FirstTestHandler" to RoutingObjectFactory { _, _, _ -> child1 },
                            "SecondTestHandler" to RoutingObjectFactory { _, _, _ -> child2 }
                    )
            )

            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    routes:
                      - prefix: /foo
                        destination:
                          type: FirstTestHandler
                          config:
                            na: na
                      - prefix: /bar
                        destination:
                          type: SecondTestHandler
                          config:
                            na: na
                """.trimIndent())

            PathPrefixRouter.Factory().build(listOf(), context.get(), routingDef)
                    .stop()

            verify(exactly = 1) { child1.stop() }
            verify(exactly = 1) { child2.stop() }
        }

        scenario("Does not call stop() referenced handlers") {
            val child1 = mockObject()
            val child2 = mockObject()

            val context = RoutingObjectFactoryContext(
                    routeRefLookup = routeLookup {
                        ref("destinationNameOne" to child1)
                        ref("destinationNameTwo" to child2)
                    }
            )

            val routingDef = routingObjectDef("""
                  type: PathPrefixRouter
                  config:
                    routes:
                      - prefix: /foo
                        destination: destinationNameOne
                      - prefix: /bar
                        destination: destinationNameTwo
                """.trimIndent())

            PathPrefixRouter.Factory().build(listOf(), context.get(), routingDef)
                    .stop()

            verify(exactly = 0) { child1.stop() }
            verify(exactly = 0) { child2.stop() }
        }
    }

})

