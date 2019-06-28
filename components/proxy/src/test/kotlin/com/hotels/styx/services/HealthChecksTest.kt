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
package com.hotels.styx.services

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.ProviderObjectRecord
import com.hotels.styx.routing.handlers.StaticResponseHandler
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.milliseconds
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import org.pcollections.HashTreePMap
import org.pcollections.HashTreePSet
import org.pcollections.PMap
import org.pcollections.PSet
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis


class HealthChecksTest : FeatureSpec({

    feature("Probe function") {
        scenario("Returns true when object is responsive") {
            val staticResponse = StaticResponseHandler(200, "Hello")

            urlProbe(get("/healthcheck.txt").build(), 1.seconds)
                    .invoke(staticResponse)
                    .toMono()
                    .block()!!.shouldBeTrue()
        }

        scenario("Returns false when object is unresponsive for N milliseconds") {
            val neverRespond = NeverHandler()

            val duration = measureTimeMillis {
                urlProbe(get("/healthcheck.txt").build(), 100.milliseconds)
                        .invoke(neverRespond)
                        .toMono()
                        .block()!!.shouldBeFalse()
            }

            duration shouldBeGreaterThan 100
        }

        scenario("Returns false when responds with 4xx error code") {
            val errorHandler = StaticResponseHandler(400, "Hello")

            urlProbe(get("/healthcheck.txt").build(), 100.milliseconds)
                    .invoke(errorHandler)
                    .toMono()
                    .block()!!.shouldBeFalse()
        }

        scenario("Returns false when responds with 5xx error code") {
            val errorHandler = StaticResponseHandler(500, "Hello")

            urlProbe(get("/healthcheck.txt").build(), 100.milliseconds)
                    .invoke(errorHandler)
                    .toMono()
                    .block()!!.shouldBeFalse()
        }
    }

    feature("State transition function") {
        val check = healthCheckFunction(2, 3)

        scenario("Transitions to Active after N consecutive positive probes") {
            check(ObjectInactive(0), true) shouldBe ObjectInactive(1)
            check(ObjectInactive(1), true) shouldBe ObjectActive(0)
        }

        scenario("An negative probe resets successful probes count") {
            check(ObjectInactive(1), false) shouldBe ObjectInactive(0)
        }

        scenario("An negative probe doesn't affect successful probes in Inactive state") {
            check(ObjectInactive(0), false) shouldBe ObjectInactive(0)
        }

        scenario("Transitions to Inactive after N consecutive negative probes") {
            check(ObjectActive(0), false) shouldBe ObjectActive(1)
            check(ObjectActive(1), false) shouldBe ObjectActive(2)
            check(ObjectActive(2), false) shouldBe ObjectInactive(0)
        }

        scenario("A successful probe resets inactive count") {
            check(ObjectActive(2), true) shouldBe ObjectActive(0)
        }

        scenario("A successful probe doesn't affect negative probes in Active state") {
            check(ObjectActive(0), true) shouldBe ObjectActive(0)
        }
    }

    feature("CheckOriginState") {
        val db = styxObjectStore<RoutingObjectRecord> {
            "aaa-01" to RoutingObjectRecord.create("StaticResponse", setOf("aaa"), mockk(), StaticResponseHandler(200, "hello"))
            "aaa-02" to RoutingObjectRecord.create("StaticResponse", setOf("aaa"), mockk(), StaticResponseHandler(200, "hello"))
            "aaa-03" to RoutingObjectRecord.create("StaticResponse", setOf("aaa"), mockk(), StaticResponseHandler(200, "hello"))
            "aaa-04" to RoutingObjectRecord.create("StaticResponse", setOf("aaa"), mockk(), StaticResponseHandler(200, "hello"))
        }

        val monitoredObjects = AtomicReference<PSet<String>>(HashTreePSet.empty())
        val objectStates = AtomicReference<PMap<String, ObjectHealth>>(HashTreePMap.empty())

        db.watch()
                .toFlux()
                .subscribe { snapshot ->

                    val names = snapshot.entrySet()
                            .filter { it.value.tags.contains("aaa") }
                            .filter { !it.value.tags.contains("state:disabled") }
                            .map { it.key }
                            .toSet()

                    val v2 = HashTreePSet.from(names)
                    val v1 = monitoredObjects.get()

                    val newObjects = v2.minusAll(v1)
                    val removedObjects = v1.minusAll(v2)

                    objectStates.updateAndGet {
                        it.minusAll(removedObjects)
                                .plusAll(newObjects
                                        .map { it to ObjectInactive(0) }
                                        .toMap())
                    }
                }
    }

})

internal fun StyxObjectStore<RoutingObjectRecord>.record(key: String, type: String, tags: Set<String>, config: JsonNode, routingObject: RoutingObject) {
    insert(key, RoutingObjectRecord.create(type, tags, config, routingObject))
}

internal fun StyxObjectStore<ProviderObjectRecord>.record(key: String, type: String, tags: Set<String>, config: JsonNode, styxService: StyxService) {
    insert(key, ProviderObjectRecord(type, tags, config, styxService))
}

internal fun <T> styxObjectStore(init: StyxObjectStore<T>.() -> Unit): StyxObjectStore<T> {
    val db = StyxObjectStore<T>()
    db.init()
    return db
}

internal fun <T> styxObjectStoreOld(init: StyxObjectStore<T>.() -> Unit): StyxObjectStore<T> =
    StyxObjectStore<T>()
            .also{
                it.init()
            }



class NeverHandler : RoutingObject {
    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> = Eventual(Mono.never())
}