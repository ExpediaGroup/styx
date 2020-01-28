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

import com.hotels.styx.api.Buffer
import com.hotels.styx.api.ByteStream
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.routing.RoutingMetadataDecorator
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.handle
import com.hotels.styx.requestContext
import com.hotels.styx.routing.handlers.RouteRefLookup.RouteDbRefLookup
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Flux
import reactor.core.publisher.toMono
import reactor.test.publisher.PublisherProbe
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional

class RouteRefLookupTest : StringSpec({
    "Retrieves handler from route database" {
        val handler = RoutingMetadataDecorator(mockk())

        val routeDb = mockk<StyxObjectStore<RoutingObjectRecord>>()
        every { routeDb.get(any()) } returns Optional.of(RoutingObjectRecord("StaticResponseHandler", mockk(), mockk(), handler))

        RouteDbRefLookup(routeDb).apply(StyxObjectReference("handler1")) shouldBe handler
    }

    "Returns error handler when route object is not found" {
        val routeDb = mockk<StyxObjectStore<RoutingObjectRecord>>()
        every { routeDb.get(any()) } returns Optional.empty()

        val response = RouteDbRefLookup(routeDb).apply(StyxObjectReference("handler1"))
                .handle(get("/").build())
                .toMono()
                .block()

        response!!.status() shouldBe NOT_FOUND
        response.bodyAs(UTF_8) shouldBe "Not found: handler1"
    }

    "Error handler consumes the request body" {
        val routeDb = mockk<StyxObjectStore<RoutingObjectRecord>>()
        every { routeDb.get(any()) } returns Optional.empty()

        val probe = PublisherProbe.of(
                Flux.just(
                        Buffer("aaa", UTF_8),
                        Buffer("bbb", UTF_8)))

        val response = RouteDbRefLookup(routeDb).apply(StyxObjectReference("handler1"))
                .handle(LiveHttpRequest.post("/")
                        .body(ByteStream(probe.flux()))
                        .build(), requestContext())
                .toMono()
                .flatMap { it.aggregate(1000).toMono() }
                .block()

        response!!.status() shouldBe NOT_FOUND
        response.bodyAs(UTF_8) shouldBe "Not found: handler1"

        probe.wasSubscribed() shouldBe true
        probe.wasRequested() shouldBe true
        probe.wasCancelled() shouldBe false
    }

})