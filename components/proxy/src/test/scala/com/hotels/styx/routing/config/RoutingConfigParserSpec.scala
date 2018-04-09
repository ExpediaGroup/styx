/*
  Copyright (C) 2013-2018 Expedia Inc.

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
import org.scalatest.{FunSpec, ShouldMatchers}


class RoutingConfigParserSpec extends FunSpec with ShouldMatchers {

  it("Parses strings as RoutingConfigReferences") {
    val jsonNode = jsonNodeFromConfig(
      """
        |config: aString
      """.stripMargin)

    val routingObjectRef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

    routingObjectRef shouldBe a[RouteHandlerReference]
    routingObjectRef.asInstanceOf[RouteHandlerReference].name() should be("aString")
  }

  it("Parses routing object definitions") {
    val jsonNode = jsonNodeFromConfig(
      """
        |config:
        |  name: main-router
        |  type: ConditionRouter
        |  config:
        |    routes:
        |        - condition: isSecure() == true
        |          destination:
        |            name: proxy-and-log-to-https
        |            type: InterceptorPipeline
        |            config:
        |              pipeline:
        |              - log
        |              - type: Rewrite
        |                config:
        |                - urlPattern: "/hwa/(.*)/foobar/(.*)"
        |                  replacement: "/$1/barfoo/$2"
        |              - type: BackendServiceProxy
        |                config:
        |                  originsFile: /path/to/https-origins.yml
        |    fallback:
        |        name: proxy-to-http
        |        type: BackendServiceProxy
        |        config:
        |          originsFile: /path/to/http-origins.yml
        |
        """.stripMargin)

    val routingObjectDef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

    routingObjectDef shouldBe a[RouteHandlerDefinition]
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].name() should be("main-router")
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].`type`() should be("ConditionRouter")
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].config() shouldBe a[JsonNode]
  }

  it("Routing object name is optional") {
    val jsonNode = jsonNodeFromConfig(
      """
        |config:
        |  type: ConditionRouter
        |  config:
        |    routes: omitted
        |
        """.stripMargin)

    val routingObjectDef = RoutingConfigParser.toRoutingConfigNode(jsonNode)

    routingObjectDef shouldBe a[RouteHandlerDefinition]
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].name() should be("")
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].`type`() should be("ConditionRouter")
    routingObjectDef.asInstanceOf[RouteHandlerDefinition].config() shouldBe a[JsonNode]
  }

  it("Routing object type is mandatory") {
    val jsonNode = jsonNodeFromConfig(
      """
        |config:
        |  name: foo
        |  config:
        |    routes: omitted
        |
        """.stripMargin)

    val e = intercept[IllegalArgumentException] {
      val routingObjectDef = RoutingConfigParser.toRoutingConfigNode(jsonNode)
    }
    e.getMessage should be("Routing config definition must have a 'type' attribute in def='foo'")
  }

  private def jsonNodeFromConfig(text: String) = new YamlConfig(text).get("config", classOf[JsonNode]).get()

}
