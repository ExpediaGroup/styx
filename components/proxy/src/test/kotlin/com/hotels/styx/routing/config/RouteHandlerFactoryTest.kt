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
package com.hotels.styx.routing.config

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.HttpHandler
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.IllegalArgumentException


class RouteHandlerFactoryTest : StringSpec({

    val mockHandler = mockk<HttpHandler>()
    val aHandlerInstance = mockk<HttpHandler>()

    val handlers = mapOf("aHandler" to aHandlerInstance)


    "Builds a new handler as per RoutingObjectDefinition" {
        val routeDef = RoutingObjectDefinition("handler-def", "DelegateHandler", mockk<JsonNode>())
        val handlerFactory = httpHandlerFactory(mockHandler)

        val routeFactory = RouteHandlerFactory(mapOf("DelegateHandler" to handlerFactory), handlers)

        val delegateHandler = routeFactory.build(listOf("parents"), routeDef)

        (delegateHandler != null).shouldBe(true)

        verify {
            handlerFactory.build(listOf("parents"), routeFactory, routeDef)
        }
    }

    "Doesn't accept unregistered types" {
        val config = RoutingObjectDefinition("foo", "ConfigType", mockk())
        val routeFactory = RouteHandlerFactory(mapOf(), handlers)

        val e = shouldThrow<IllegalArgumentException> {
            routeFactory.build(listOf(), config)
        }

        e.message.shouldBe("Unknown handler type 'ConfigType'")
    }

    "Returns handler from a configuration reference" {
        val routeFactory = RouteHandlerFactory(mapOf(), handlers)
        val handler = routeFactory.build(listOf(), RoutingObjectReference("aHandler"))
        handler.shouldBe(aHandlerInstance)
    }

    "Throws exception when it refers a non-existent object" {
        val routeFactory = RouteHandlerFactory(mapOf(), handlers)

        val e = shouldThrow<IllegalArgumentException> {
            routeFactory.build(listOf(), RoutingObjectReference("non-existent"))
        }

        e.message.shouldBe("Non-existent handler instance: 'non-existent'")
    }

})


fun httpHandlerFactory(handler: HttpHandler): HttpHandlerFactory {
    val factory: HttpHandlerFactory = mockk()

    every {
        factory.build(any(), any(), any())
    } returns handler

    return factory
}
