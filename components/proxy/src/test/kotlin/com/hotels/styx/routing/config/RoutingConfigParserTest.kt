/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.routing.config

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

fun jsonNodeFromConfig(text: String) = YamlConfig(text).get("config", JsonNode::class.java).get()

class RoutingConfigParserTest : StringSpec({
    "Parses strings as RoutingConfigReferences" {
        val jsonNode = jsonNodeFromConfig("""
            config: aString
        """.trimIndent())

        val routingObjectRef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

        routingObjectRef.shouldBeTypeOf<StyxObjectReference>()
        (routingObjectRef as StyxObjectReference).name().shouldBe("aString")
    }

    "Parses routing object definitions" {
        val jsonNode = jsonNodeFromConfig("""
            config:
              name: main-router
              type: ConditionRouter
              config:
                routes:
                    - condition: isSecure() == true
                      destination:
                        name: proxy-and-log-to-https
                        type: InterceptorPipeline
                        config:
                          pipeline:
                          - log
                          - type: Rewrite
                            config:
                            - urlPattern: "/hwa/(.*)/foobar/(.*)"
                              replacement: "/${'$'}1/barfoo/${'$'}2"
                          - type: BackendServiceProxy
                            config:
                              originsFile: /path/to/https-origins.yml
                fallback:
                    name: proxy-to-http
                    type: BackendServiceProxy
                    config:
                      originsFile: /path/to/http-origins.yml
        """.trimIndent())

        val routingObjectDef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

        routingObjectDef.shouldBeTypeOf<StyxObjectDefinition>()

        (routingObjectDef as StyxObjectDefinition).name() shouldBe("main-router")
        routingObjectDef.type() shouldBe("ConditionRouter")

        routingObjectDef.config().shouldBeInstanceOf<JsonNode>()
    }


    "Routing object name is optional" {
        val jsonNode = jsonNodeFromConfig("""
            config:
              type: ConditionRouter
              config:
                routes: omitted
        """.trimIndent())

        val routingObjectDef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

        routingObjectDef.shouldBeTypeOf<StyxObjectDefinition>()
        (routingObjectDef as StyxObjectDefinition).name() shouldBe ("")
        routingObjectDef.type() shouldBe("ConditionRouter")
        routingObjectDef.config().shouldBeInstanceOf<JsonNode>()
    }

    "Routing object type is mandatory" {
        val jsonNode = jsonNodeFromConfig("""
            config:
              name: foo
              config:
                routes: omitted
        """.trimIndent())

        val e = shouldThrow<java.lang.IllegalArgumentException> {
            RoutingConfigParser.toRoutingConfigNode(jsonNode)
        }
        e.message shouldBe("Routing config definition must have a 'type' attribute in def='foo'")
    }

})
