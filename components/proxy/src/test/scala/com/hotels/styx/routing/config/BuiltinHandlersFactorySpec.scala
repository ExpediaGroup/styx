/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.routing.config

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.HttpHandler2
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}

import scala.collection.JavaConverters._

class BuiltinHandlersFactorySpec extends FunSpec with ShouldMatchers with MockitoSugar {

  private val handler = mock[HttpHandler2]

  it ("builds the handler using specified type") {
    val config = new RoutingConfigDefinition("My-Delegate", "DelegateHandler", mock[JsonNode])
    val delegateHandlerBuilder = handlerFactory()
    val objectFactory = new BuiltinHandlersFactory(Map("DelegateHandler" -> delegateHandlerBuilder).asJava)

    val delegateHandler = objectFactory.build(List("parents").asJava, config)

    (delegateHandler != null) should be (true)
    verify(delegateHandlerBuilder).build(List("parents").asJava, objectFactory, config)
  }

  it ("doesn't accept unregistered types") {
    val config = new RoutingConfigDefinition("foo", "ConfigType", mock[JsonNode])
    val objectFactory = new BuiltinHandlersFactory(Map.empty[String, HttpHandlerFactory].asJava)

    val e = intercept[IllegalArgumentException] {
      objectFactory.build(List().asJava, config)
    }

    e.getMessage should be ("Unknown handler type 'ConfigType'")
  }

  it ("doesn't accept reference types") {
    val objectFactory = new BuiltinHandlersFactory(Map.empty[String, HttpHandlerFactory].asJava)

    val e = intercept[UnsupportedOperationException] {
      objectFactory.build(List().asJava, new RoutingConfigReference("foo"))
    }

    e.getMessage should be ("Routing config node must be an config block, not a reference")
  }

  private def handlerFactory(): HttpHandlerFactory = {
    val mockFactory: HttpHandlerFactory = mock[HttpHandlerFactory]
    when(mockFactory.build(any[java.util.List[String]], any[BuiltinHandlersFactory], any[RoutingConfigDefinition])).thenReturn(handler)
    mockFactory
  }

  private def yamlConfig(text: String) = new YamlConfig(text)

}
