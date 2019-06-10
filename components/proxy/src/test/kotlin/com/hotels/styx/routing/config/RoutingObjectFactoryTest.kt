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
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectFactory.DEFAULT_REFERENCE_LOOKUP
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.server.HttpInterceptorContext
import io.kotlintest.matchers.withClue
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono
import java.util.Optional

class RoutingObjectFactoryTest : StringSpec({

    val mockHandler = mockk<RoutingObject> {
        every { handle(any(), any()) } returns Eventual.of(LiveHttpResponse.response(OK).build())
    }

    val routeObjectStore = mockk<StyxObjectStore<RoutingObjectRecord>>()
    every { routeObjectStore.get("aHandler") } returns Optional.of(RoutingObjectRecord.create("type", mockk(), mockk(), mockHandler))
    
    "Builds a new handler as per RoutingObjectDefinition" {
        val routeDef = RoutingObjectDefinition("handler-def", "SomeRoutingObject", mockk())
        val handlerFactory = httpHandlerFactory(mockHandler)

        val routingObjectFactory = RoutingObjectFactory(
                DEFAULT_REFERENCE_LOOKUP,
                mapOf("SomeRoutingObject" to handlerFactory))

        withClue("Should create a routing object handler") {
            val handler = routingObjectFactory.build(listOf("parents"), routeDef)
            (handler != null).shouldBe(true)
        }

        verify {
            handlerFactory.build(listOf("parents"), any(), routeDef)
        }
    }

    "Doesn't accept unregistered types" {
        val config = RoutingObjectDefinition("foo", "ConfigType", mockk())
        val routingObjectFactory = RoutingObjectFactory(routeObjectStore)

        val e = shouldThrow<IllegalArgumentException> {
            routingObjectFactory.build(listOf(), config)
        }

        e.message.shouldBe("Unknown handler type 'ConfigType'")
    }

    "Returns handler from a configuration reference" {
        val routeDb = mapOf("aHandler" to RoutingObject { request, context -> Eventual.of(response(OK).build().stream()) })
        val routingObjectFactory = RoutingObjectFactory( { ref -> routeDb[ref.name()] } )

        val handler = routingObjectFactory.build(listOf(), RoutingObjectReference("aHandler"))

        val response = handler.handle(get("/").build().stream(), HttpInterceptorContext.create())
                .toMono()
                .block()

        response?.status() shouldBe (OK)
    }

    "Looks up handler for every request" {
        val referenceLookup = mockk<RouteRefLookup>()
        every {referenceLookup.apply(RoutingObjectReference("aHandler")) } returns RoutingObject { request, context -> Eventual.of(response(OK).build().stream()) }

        val routingObjectFactory = RoutingObjectFactory(referenceLookup)

        val handler = routingObjectFactory.build(listOf(), RoutingObjectReference("aHandler"))

        handler.handle(get("/").build().stream(), HttpInterceptorContext.create()).toMono().block()
        handler.handle(get("/").build().stream(), HttpInterceptorContext.create()).toMono().block()
        handler.handle(get("/").build().stream(), HttpInterceptorContext.create()).toMono().block()
        handler.handle(get("/").build().stream(), HttpInterceptorContext.create()).toMono().block()

        verify(exactly = 4) {
            referenceLookup.apply(RoutingObjectReference("aHandler"))
        }
    }

})


fun httpHandlerFactory(handler: RoutingObject): HttpHandlerFactory {
    val factory: HttpHandlerFactory = mockk()

    every {
        factory.build(any(), any(), any())
    } returns handler

    return factory
}
