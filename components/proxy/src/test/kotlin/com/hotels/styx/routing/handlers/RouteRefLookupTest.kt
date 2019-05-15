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
package com.hotels.styx.routing.handlers

import com.hotels.styx.admin.handlers.toMono
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handle
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Optional

class RouteRefLookupTest : StringSpec({
    "Retrieves handler from route database" {
        val handler = mockk<HttpHandler>()

        val routeDb = mockk<StyxObjectStore<RoutingObjectRecord>>()
        every { routeDb.get(any()) } returns Optional.of(RoutingObjectRecord("StaticResponseHandler", mockk(), handler))

        RouteRefLookup(routeDb).apply(RoutingObjectReference("handler1")) shouldBe handler
    }

    "Returns error handler when route object is not found" {
        val routeDb = mockk<StyxObjectStore<RoutingObjectRecord>>()
        every { routeDb.get(any()) } returns Optional.empty()

        val response = RouteRefLookup(routeDb).apply(RoutingObjectReference("handler1"))
                .handle(get("/").build())
                .toMono()
                ?.block()

        response?.status() shouldBe NOT_FOUND
        response?.bodyAs(UTF_8) shouldBe "Not found: handler1"
    }

})