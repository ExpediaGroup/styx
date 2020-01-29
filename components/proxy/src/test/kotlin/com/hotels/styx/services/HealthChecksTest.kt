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
package com.hotels.styx.services

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaders
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.requestContext
import com.hotels.styx.routing.handlers.StaticResponseHandler
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.milliseconds
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import kotlin.system.measureTimeMillis


class HealthChecksTest : FeatureSpec({

    feature("Probe function") {
        val headers = HttpHeaders.Builder().build();

        scenario("Returns true when object is responsive") {
            val staticResponse = StaticResponseHandler(200, "Hello", headers)

            urlProbe(get("/healthcheck.txt").build(), 1.seconds, requestContext())
                    .invoke(staticResponse)
                    .toMono()
                    .block()!!.shouldBeTrue()
        }

        scenario("Returns false when object is unresponsive for N milliseconds") {
            val neverRespond = NeverHandler()

            val duration = measureTimeMillis {
                urlProbe(get("/healthcheck.txt").build(), 100.milliseconds, requestContext())
                        .invoke(neverRespond)
                        .toMono()
                        .block()!!.shouldBeFalse()
            }

            duration shouldBeGreaterThan 100
        }

        scenario("Returns false when responds with 4xx error code") {
            val errorHandler = StaticResponseHandler(400, "Hello", headers)

            urlProbe(get("/healthcheck.txt").build(), 100.milliseconds, requestContext())
                    .invoke(errorHandler)
                    .toMono()
                    .block()!!.shouldBeFalse()
        }

        scenario("Returns false when responds with 5xx error code") {
            val errorHandler = StaticResponseHandler(500, "Hello", headers)

            urlProbe(get("/healthcheck.txt").build(), 100.milliseconds, requestContext())
                    .invoke(errorHandler)
                    .toMono()
                    .block()!!.shouldBeFalse()
        }
    }

    feature("State transition function") {
        val check = healthCheckFunction(2, 3)

        scenario("Transitions to Active after N consecutive positive probes") {
            check(ObjectUnreachable(0), true) shouldBe ObjectUnreachable(1)
            check(ObjectUnreachable(1), true) shouldBe ObjectActive(0)
        }

        scenario("An negative probe resets successful probes count") {
            check(ObjectUnreachable(1), false) shouldBe ObjectUnreachable(0)
        }

        scenario("An negative probe doesn't affect successful probes in Inactive state") {
            check(ObjectUnreachable(0), false) shouldBe ObjectUnreachable(0)
        }

        scenario("Transitions to Inactive after N consecutive negative probes") {
            check(ObjectActive(0), false) shouldBe ObjectActive(1)
            check(ObjectActive(1), false) shouldBe ObjectActive(2)
            check(ObjectActive(2), false) shouldBe ObjectUnreachable(0)
        }

        scenario("A successful probe resets inactive count") {
            check(ObjectActive(2), true) shouldBe ObjectActive(0)
        }

        scenario("A successful probe doesn't affect negative probes in Active state") {
            check(ObjectActive(0), true) shouldBe ObjectActive(0)
        }
    }

})

internal fun StyxObjectStore<RoutingObjectRecord>.record(key: String, type: String, tags: Set<String>, config: JsonNode, routingObject: RoutingObject) {
    insert(key, RoutingObjectRecord.create(type, tags, config, routingObject))
}

internal fun StyxObjectStore<ProviderObjectRecord>.record(key: String, type: String, tags: Set<String>, config: JsonNode, styxService: StyxService) {
    insert(key, ProviderObjectRecord(type, tags, config, styxService))
}

class NeverHandler : RoutingObject {
    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> = Eventual(Mono.never())
}