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
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class RouteHandlerFactorySpec extends FunSpec with ShouldMatchers with MockitoSugar {

  private val mockHandler = mock[HttpHandler]
  private val aHandlerInstance = mock[HttpHandler]

  val handlers = Map[String, HttpHandler](
    "aHandler" -> aHandlerInstance
  )

  it ("Builds a new handler as per RouteHandlerDefinition") {
    val routeDef = new RouteHandlerDefinition("handler-def", "DelegateHandler", mock[JsonNode])
    val handlerFactory = httpHandlerFactory()

    val routeFactory = new RouteHandlerFactory(Map("DelegateHandler" -> handlerFactory).asJava, handlers)

    val delegateHandler = routeFactory.build(List("parents").asJava, routeDef)

    (delegateHandler != null) should be (true)
    verify(handlerFactory).build(List("parents").asJava, routeFactory, routeDef)
  }

  it ("Doesn't accept unregistered types") {
    val config = new RouteHandlerDefinition("foo", "ConfigType", mock[JsonNode])
    val routeFactory = new RouteHandlerFactory(Map.empty[String, HttpHandlerFactory].asJava, handlers)

    val e = intercept[IllegalArgumentException] {
      routeFactory.build(List().asJava, config)
    }

    e.getMessage should be ("Unknown handler type 'ConfigType'")
  }

  it ("Returns handler from a configuration reference") {
    val routeFactory = new RouteHandlerFactory(Map.empty[String, HttpHandlerFactory].asJava, handlers)

    val handler = routeFactory.build(List().asJava, new RouteHandlerReference("aHandler"))

    handler should be (aHandlerInstance)
  }

  it ("Throws exception when it refers a non-existent object") {
    val routeFactory = new RouteHandlerFactory(Map.empty[String, HttpHandlerFactory].asJava, handlers)

    val e = intercept[IllegalArgumentException] {
      routeFactory.build(List().asJava, new RouteHandlerReference("non-existent"))
    }

    e.getMessage should be("Non-existent handler instance: 'non-existent'")
  }

  private def httpHandlerFactory(): HttpHandlerFactory = {
    val mockFactory: HttpHandlerFactory = mock[HttpHandlerFactory]
    when(mockFactory.build(any[java.util.List[String]], any[RouteHandlerFactory], any[RouteHandlerDefinition])).thenReturn(mockHandler)
    mockFactory
  }

  private def yamlConfig(text: String) = new YamlConfig(text)

}
