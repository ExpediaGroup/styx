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

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin
import com.hotels.styx.routing.RoutingContext
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.interceptors.RewriteInterceptor
import com.hotels.styx.routing.routingObjectDef
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Mono

class HttpInterceptorPipelineTest : StringSpec({
    val hwaRequest = LiveHttpRequest.get("/x").build()
    val routeDatabase = StyxObjectStore<RoutingObjectRecord>()

    "it errors when there is a reference to non-existing pipeline" {

        val e = shouldThrow<java.lang.IllegalArgumentException> {
            HttpInterceptorPipeline.Factory().build(
                    listOf("config"),
                    RoutingContext(
                            plugins = listOf(
                                    namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                                    namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                            ),
                            routeDb = routeDatabase,
                            routingObjectFactory = routingObjectFactory())
                            .get(),
                    routingObjectDef("""
                          type: InterceptorPipeline
                          config:
                            pipeline:
                              - non-existing
                            handler:
                              name: MyHandler
                              type: Foo
                              config:
                                bar
                        """.trimIndent()))
        }

        e.message shouldBe ("No such plugin or interceptor exists, attribute='config.pipeline', name='non-existing'")
    }

    "it errors when handler configuration is missing" {

        val e = shouldThrow<IllegalArgumentException> {
            HttpInterceptorPipeline.Factory().build(
                    listOf("config"),
                    RoutingContext(
                            plugins = listOf(
                                    namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                                    namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                            ),
                            routeDb = routeDatabase,
                            routingObjectFactory = routingObjectFactory())
                            .get(),
                    routingObjectDef("""
                          type: InterceptorPipeline
                          config:
                            pipeline:
                              - interceptor2
                        """.trimIndent()))
        }

        e.message shouldBe ("Routing object definition of type 'InterceptorPipeline', attribute='config', is missing a mandatory 'handler' attribute.")
    }

    "it builds an interceptor pipeline from the configuration" {

        val handler = HttpInterceptorPipeline.Factory().build(
                listOf("config"),
                RoutingContext(
                        plugins = listOf(
                                namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                                namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })
                        ),
                        routeDb = routeDatabase,
                        routingObjectFactory = routingObjectFactory())
                        .get(),
                routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        pipeline:
                          - interceptor1
                          - interceptor2
                        handler:
                          name: MyHandler
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                    """.trimIndent()))

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
    }

    "it Treats absent 'pipeline' attribute as empty pipeline" {

        val handler = HttpInterceptorPipeline.Factory().build(
                listOf("config"),
                RoutingContext(
                        plugins = listOf(),
                        routeDb = routeDatabase,
                        routingObjectFactory = routingObjectFactory())
                        .get(),
                routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler:
                          name: MyHandler
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                      """.trimIndent()))

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.status() shouldBe (OK)
    }

    "Fallback handler can be an object reference" {
        val handler = HttpInterceptorPipeline.Factory().build(
                listOf("config"),
                RoutingContext(
                        plugins = listOf(),
                        routeDb = routeDatabase,
                        routingObjectFactory = routingObjectFactory())
                        .get(),
                routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler: referenceToAnotherRoutingObject
                      """.trimIndent()))

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.status() shouldBe NOT_FOUND
    }

    "Supports inline interceptor definitions" {

        val handler = HttpInterceptorPipeline.Factory().build(listOf("config"),
                RoutingContext(
                        plugins = listOf(
                                namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                                namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })
                        ),
                        routeDb = routeDatabase,
                        routingObjectFactory = routingObjectFactory(),
                        interceptorsFactory = BuiltinInterceptorsFactory(mapOf("Rewrite" to RewriteInterceptor.Factory()))
                ).get(),
                routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        pipeline:
                          - interceptor1
                          - name: rewrite
                            type: Rewrite
                            config:
                            - urlPattern:  /(.*)
                              replacement: /app/$1
                          - interceptor2
                        handler:
                          name: MyHandler
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                     """.trimIndent()))

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
    }


    "passes full configuration attribute path (config.config.handler) to the builtins factory" {

        val builtinsFactory = mockk<RoutingObjectFactory> {
            every { build(any(), any()) } returns HttpHandler { _, _ -> Eventual.of(response(OK).build()) }
        }


        HttpInterceptorPipeline.Factory().build(
                listOf("config", "config"),
                RoutingContext(
                        plugins = listOf(),
                        routeDb = routeDatabase,
                        routingObjectFactory = builtinsFactory)
                        .get(),
                routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler:
                          name: MyHandler
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                    """.trimIndent()))

        verify {
            builtinsFactory.build(listOf("config", "config", "handler"), any())
        }
    }

})


fun mockHandlerFactory(): HttpHandlerFactory {
    val handlerFactory = mockk<HttpHandlerFactory>()

    every {
        handlerFactory.build(any(), any(), any())
    } returns HttpHandler { _, _ -> Eventual.of(LiveHttpResponse.response(OK).build()) }

    return handlerFactory
}

fun routingObjectFactory() = RoutingObjectFactory(mapOf("BackendServiceProxy" to mockHandlerFactory()), mockk(), StyxObjectStore(), mockk(), mockk(), false)
