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
package com.hotels.styx.routing

import com.hotels.styx.Environment
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.WebServiceHandler
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_FACTORIES
import com.hotels.styx.routing.config.Builtins.DEFAULT_REFERENCE_LOOKUP
import com.hotels.styx.routing.config.Builtins.INTERCEPTOR_FACTORIES
import com.hotels.styx.routing.config.HttpInterceptorFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.RoutingObjectDefinition
import com.hotels.styx.routing.config.RoutingObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.server.HttpInterceptorContext
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture

fun routingObjectDef(text: String) = YamlConfig(text).`as`((RoutingObjectDefinition::class.java))

internal data class RoutingObjectFactoryContext(
        val routeRefLookup: RouteRefLookup = DEFAULT_REFERENCE_LOOKUP,
        val environment: Environment = Environment.Builder().build(),
        val objectStore: StyxObjectStore<RoutingObjectRecord> = StyxObjectStore(),
        val objectFactories: Map<String, RoutingObjectFactory> = BUILTIN_HANDLER_FACTORIES,
        val plugins: Iterable<NamedPlugin> = listOf(),
        val interceptorFactories: Map<String, HttpInterceptorFactory> = INTERCEPTOR_FACTORIES,
        val requestTracking: Boolean = false) {
    fun get() = RoutingObjectFactory.Context(routeRefLookup, environment, objectStore, objectFactories, plugins, INTERCEPTOR_FACTORIES, requestTracking)

}

fun WebServiceHandler.handle(request: HttpRequest) = this.handle(request, HttpInterceptorContext.create())

fun HttpHandler.handle(request: HttpRequest, count: Int = 10000) = this.handle(request.stream(), HttpInterceptorContext.create())
        .flatMap { it.aggregate(count) }

// DSL for routing object database & object creation:
fun HashMap<RoutingObjectReference, RoutingObject>.ref(pair: Pair<String, RoutingObject>) {
    put(RoutingObjectReference(pair.first), pair.second)
}

fun routeLookup(block: HashMap<RoutingObjectReference, RoutingObject>.() -> Unit): RouteRefLookup {
    val refLookup = HashMap<RoutingObjectReference, RoutingObject>()
    refLookup.block()
    return RouteRefLookup { refLookup[it] }
}

fun mockObject(content: String = "", capture: ArgumentCapture = DontCapture) = mockk<RoutingObject> {
    every {
        handle(when {
            capture is CaptureSlot -> capture(capture.slot)
            capture is CaptureList -> capture(capture.list)
            else -> any()
        }, any())
    } returns Eventual.of(HttpResponse.response(OK).body(content, UTF_8).build().stream())

    every { stop() } returns CompletableFuture.completedFuture(null)
}

fun failingMockObject() = mockk<RoutingObject> {
    every { handle(any(), any()) } returns Eventual.error(RuntimeException("Error occurred!"))
    every { stop() } returns CompletableFuture.completedFuture(null)
}

fun mockObjectFactory(objects: List<RoutingObject>) = mockk<RoutingObjectFactory> {
    every { build(any(), any(), any()) } returnsMany objects
}

sealed class ArgumentCapture
data class CaptureSlot(val slot: CapturingSlot<LiveHttpRequest>) : ArgumentCapture()
data class CaptureList(val list: MutableList<LiveHttpRequest>) : ArgumentCapture()
object DontCapture : ArgumentCapture()
