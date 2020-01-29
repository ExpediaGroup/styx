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
package com.hotels.styx.routing.config

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.requestContext
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.serviceproviders.ServiceProviderFactory
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.matchers.withClue
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono
import java.util.Optional

class BuiltinsTest : StringSpec({

    val mockHandler = mockk<RoutingObject> {
        every { handle(any(), any()) } returns Eventual.of(LiveHttpResponse.response(OK).build())
    }

    val routeObjectStore = mockk<StyxObjectStore<RoutingObjectRecord>>()
    every { routeObjectStore.get("aHandler") } returns Optional.of(RoutingObjectRecord.create("type", setOf(), mockk(), mockHandler))

    "Builds a new handler as per StyxObjectDefinition" {
        val routeDef = StyxObjectDefinition("handler-def", "SomeRoutingObject", mockk())
        val handlerFactory = httpHandlerFactory(mockHandler)

        val context = RoutingObjectFactoryContext(objectFactories = mapOf("SomeRoutingObject" to handlerFactory))

        withClue("Should create a routing object handler") {
            val handler = Builtins.build(listOf("parents"), context.get(), routeDef)
            (handler != null).shouldBe(true)
        }

        verify {
            handlerFactory.build(listOf("parents"), any(), routeDef)
        }
    }

    "Doesn't accept unregistered types" {
        val config = StyxObjectDefinition("foo", "ConfigType", mockk())
        val context = RoutingObjectFactoryContext(objectStore = routeObjectStore)

        val e = shouldThrow<IllegalArgumentException> {
            Builtins.build(listOf(), context.get(), config)
        }

        e.message.shouldBe("Unknown handler type 'ConfigType'")
    }

    "Returns handler from a configuration reference" {
        val routeDb = mapOf("aHandler" to RoutingObject { request, context -> Eventual.of(response(OK).build().stream()) })

        val context = RoutingObjectFactoryContext(routeRefLookup = RouteRefLookup { ref -> routeDb[ref.name()] })

        val handler = Builtins.build(listOf(), context.get(), StyxObjectReference("aHandler"))

        handler.handle(get("/").build().stream(), requestContext())
                .toMono()
                .block()!!
                .status() shouldBe (OK)
    }

    "Looks up handler for every request" {
        val referenceLookup = mockk<RouteRefLookup>()
        every { referenceLookup.apply(StyxObjectReference("aHandler")) } returns RoutingObject { request, context -> Eventual.of(response(OK).build().stream()) }

        val context = RoutingObjectFactoryContext(routeRefLookup = referenceLookup)

        val handler = Builtins.build(listOf(), context.get(), StyxObjectReference("aHandler"))

        handler.handle(get("/").build().stream(), requestContext()).toMono().block()
        handler.handle(get("/").build().stream(), requestContext()).toMono().block()
        handler.handle(get("/").build().stream(), requestContext()).toMono().block()
        handler.handle(get("/").build().stream(), requestContext()).toMono().block()

        verify(exactly = 4) {
            referenceLookup.apply(StyxObjectReference("aHandler"))
        }
    }

    "ServiceProvider factory delegates to appropriate service provider factory method" {
        val factory = mockk<ServiceProviderFactory> {
            every { create(any(), any(), any(), any()) } returns mockk()
        }

        val context = RoutingObjectFactoryContext().get()
        val serviceConfig = mockk<JsonNode>()
        val serviceDb = mockk<StyxObjectStore<ProviderObjectRecord>>()

        Builtins.build(
                "healthCheckMonitor",
                StyxObjectDefinition("healthCheckMonitor", "HealthCheckMonitor", serviceConfig),
                serviceDb,
                mapOf("HealthCheckMonitor" to factory),
                context
        )

        verify { factory.create("healthCheckMonitor", context, serviceConfig, serviceDb) }
    }

    "ServiceProvider factory throws an exception for unknown service provider factory name" {
        shouldThrow<java.lang.IllegalArgumentException> {
            Builtins.build(
                    "healthCheckMonitor",
                    StyxObjectDefinition("healthMonitor", "ABC", mockk()),
                    mockk(),
                    mapOf(),
                    RoutingObjectFactoryContext().get())
                    .shouldBeInstanceOf<StyxService>()
        }.message shouldBe "Unknown service provider type 'ABC' for 'healthMonitor' provider"
    }

})


fun httpHandlerFactory(handler: RoutingObject): RoutingObjectFactory {
    val factory: RoutingObjectFactory = mockk()

    every {
        factory.build(any(), any(), any())
    } returns handler

    return factory
}
