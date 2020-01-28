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
package com.hotels.styx

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.WebServiceHandler
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_FACTORIES
import com.hotels.styx.routing.config.Builtins.DEFAULT_REFERENCE_LOOKUP
import com.hotels.styx.routing.config.Builtins.INTERCEPTOR_FACTORIES
import com.hotels.styx.routing.config.HttpInterceptorFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.RouteRefLookup
import com.hotels.styx.server.HttpInterceptorContext
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import org.slf4j.LoggerFactory
import reactor.core.publisher.toMono
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun routingObjectDef(text: String) = YamlConfig(text).`as`(StyxObjectDefinition::class.java)

fun configBlock(text: String) = YamlConfig(text).`as`(JsonNode::class.java)

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

fun WebServiceHandler.handle(request: HttpRequest) = this.handle(request, requestContext())

fun HttpHandler.handle(request: HttpRequest, count: Int = 10000) = this.handle(request.stream(), requestContext())
        .flatMap { it.aggregate(count) }

// DSL for routing object database & object creation:
fun HashMap<StyxObjectReference, RoutingObject>.ref(pair: Pair<String, RoutingObject>) {
    put(StyxObjectReference(pair.first), pair.second)
}

fun routeLookup(block: HashMap<StyxObjectReference, RoutingObject>.() -> Unit): RouteRefLookup {
    val refLookup = HashMap<StyxObjectReference, RoutingObject>()
    refLookup.block()
    return RouteRefLookup { refLookup[it] }
}

/**
 * Creates a mock routing object with specified capturing behaviour for incoming HTTP requests.
 *
 * Three possible capture behavours are:
 *
 * CaptureSlot - captures only one LiveHttpRequest
 * CaptureList - captures all LiveHttpRequest messages into a list
 * DontCapture - don't capture anything
 *
 * An example:
 *
 *         val probeRequests = mutableListOf<LiveHttpRequest>()
 *
 *         val handler = mockObject("handler-01", CaptureList(probeRequests))
 *
 *         verify(exactly = 1) { handler.handle(any(), any()) }
 *         probeRequests.map { it.url().path() } shouldBe (listOf("/healthCheck.txt", "/healthCheck.txt"))
 *
 */
fun mockObject(content: String = "", capture: ArgumentCapture = DontCapture) = mockk<RoutingObject> {
    every {
        handle(when {
            capture is CaptureSlot -> capture(capture.slot)
            capture is CaptureList -> capture(capture.list)
            else -> any()
        }, any())
    } returns Eventual.of(response(OK).body(content, UTF_8).build().stream())

    every { stop() } returns CompletableFuture.completedFuture(null)
}

sealed class ArgumentCapture
data class CaptureSlot(val slot: CapturingSlot<LiveHttpRequest>) : ArgumentCapture()
data class CaptureList(val list: MutableList<LiveHttpRequest>) : ArgumentCapture()
object DontCapture : ArgumentCapture()


/**
 * Createa a mock Routing Object that simulates request processing failures.
 */
fun failingMockObject() = mockk<RoutingObject> {
    every { handle(any(), any()) } returns Eventual.error(RuntimeException("Error occurred!"))
    every { stop() } returns CompletableFuture.completedFuture(null)
}

/**
 * Creates a mock RoutingObjectFactory. Takes a list of routing objects as its sole argument.
 * Each factory invocation will return next object from the list.
 */
fun mockObjectFactory(objects: List<RoutingObject>) = mockk<RoutingObjectFactory> {
    every { build(any(), any(), any()) } returnsMany objects
}


private val LOGGER = LoggerFactory.getLogger("ProxySupport")

fun CompletableFuture<HttpResponse>.wait(debug: Boolean = false) = this.toMono()
        .doOnNext {
            if (debug) {
                LOGGER.debug("${it.status()} - ${it.headers()} - ${it.bodyAs(UTF_8)}")
            }
        }
        .block()

fun CompletableFuture<LiveHttpResponse>.wait(debug: Boolean = false) = this.toMono()
        .doOnNext {
            if (debug) {
                LOGGER.debug("${it.status()} - ${it.headers()}")
            }
        }
        .block()

fun Eventual<LiveHttpResponse>.wait(maxBytes: Int = 100*1024, debug: Boolean = false) = this.toMono()
        .flatMap { it.aggregate(maxBytes).toMono() }
        .doOnNext {
            if (debug) {
                LOGGER.info("${it.status()} - ${it.headers()} - ${it.bodyAs(UTF_8)}")
            }
        }
        .block()

fun requestContext(secure: Boolean = false, executor: Executor = Executor { it.run() }) = HttpInterceptorContext(secure, null, executor)