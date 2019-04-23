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

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.routing.RoutingObjectRecord
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

class RoutingObjectFactoryTest : StringSpec({

    val mockHandler = mockk<HttpHandler> {
        every { handle(any(), any()) } returns Eventual.of(LiveHttpResponse.response(OK).build())
    }

    val routeObjectStore = mockk<StyxObjectStore<RoutingObjectRecord>>()
    every { routeObjectStore.get("aHandler") } returns Optional.of(RoutingObjectRecord("name", setOf(), mockk(), mockHandler))
    
    "Builds a new handler as per RoutingObjectDefinition" {
        val routeDef = RoutingObjectDefinition("handler-def", "DelegateHandler", mockk())
        val handlerFactory = httpHandlerFactory(mockHandler)

        val routeFactory = RoutingObjectFactory(mapOf("DelegateHandler" to handlerFactory), mockk(), routeObjectStore, mockk(), mockk(), false)

        val delegateHandler = routeFactory.build(listOf("parents"), routeDef)

        (delegateHandler != null).shouldBe(true)

        verify {
            handlerFactory.build(listOf("parents"), any(), routeDef)
        }
    }

    "Doesn't accept unregistered types" {
        val config = RoutingObjectDefinition("foo", "ConfigType", mockk())
        val routeFactory = RoutingObjectFactory(mapOf(), mockk(), routeObjectStore, mockk(), mockk(), false)

        val e = shouldThrow<IllegalArgumentException> {
            routeFactory.build(listOf(), config)
        }

        e.message.shouldBe("Unknown handler type 'ConfigType'")
    }

    "Returns handler from a configuration reference" {
        val routeFactory = RoutingObjectFactory(mapOf(), mockk(), routeObjectStore, mockk(), mockk(), false)
        val handler = routeFactory.build(listOf(), RoutingObjectReference("aHandler"))
        val response = Mono.from(handler.handle(LiveHttpRequest.get("/").build(), HttpInterceptorContext.create())).block()
        response?.status()?.code() shouldBe (200)
    }

})


fun httpHandlerFactory(handler: HttpHandler): HttpHandlerFactory {
    val factory: HttpHandlerFactory = mockk()

    every {
        factory.build(any(), any(), any())
    } returns handler

    return factory
}
