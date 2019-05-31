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

import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin
import com.hotels.styx.routing.RoutingContext
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.interceptors.RewriteInterceptor
import com.hotels.styx.routing.mockObject
import com.hotels.styx.routing.ref
import com.hotels.styx.routing.routeLookup
import com.hotels.styx.routing.routingObjectDef
import com.hotels.styx.routing.routingObjectFactory
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono

class HttpInterceptorPipelineTest : FeatureSpec({
    val hwaRequest = LiveHttpRequest.get("/x").build()

    feature("Factory") {
        scenario("it errors when there is a reference to non-existing pipeline") {

            val context = RoutingContext(
                    plugins = listOf(
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                    ))

            val e = shouldThrow<java.lang.IllegalArgumentException> {
                HttpInterceptorPipeline.Factory().build(
                        listOf("config"),
                        context.get(),
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

        scenario("it errors when handler configuration is missing") {
            val context = RoutingContext(
                    plugins = listOf(
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                    ))

            val e = shouldThrow<IllegalArgumentException> {
                HttpInterceptorPipeline.Factory().build(
                        listOf("config"),
                        context.get(),
                        routingObjectDef("""
                          type: InterceptorPipeline
                          config:
                            pipeline:
                              - interceptor2
                        """.trimIndent()))
            }

            e.message shouldBe ("Routing object definition of type 'InterceptorPipeline', attribute='config', is missing a mandatory 'handler' attribute.")
        }

        scenario("it builds an interceptor pipeline from the configuration") {
            val context = RoutingContext(
                    plugins = listOf(
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })))

            val handler = HttpInterceptorPipeline.Factory().build(
                    listOf("config"),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        pipeline:
                          - interceptor1
                          - interceptor2
                        handler:
                          name: MyHandler
                          type: StaticResponseHandler
                          config:
                            status: 200
                            content: hello
                    """.trimIndent()))

            val response = handler.handle(hwaRequest, null).toMono().block()
            response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
        }

        scenario("it Treats absent 'pipeline' attribute as empty pipeline") {
            val context = RoutingContext()

            val handler = HttpInterceptorPipeline.Factory().build(
                    listOf("config"),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler:
                          name: MyHandler
                          type: StaticResponseHandler
                          config:
                            status: 200
                            content: hello
                      """.trimIndent()))

            val response = handler.handle(hwaRequest, null).toMono().block()
            response?.status() shouldBe (OK)
        }

        scenario("Handler can be an object reference") {
            val context = RoutingContext(
                    factory = routingObjectFactory(
                            routeLookup {
                                ref("referenceToAnotherRoutingObject" to mockObject())
                            }
                    )
            )

            val handler = HttpInterceptorPipeline.Factory().build(
                    listOf("config"),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler: referenceToAnotherRoutingObject
                      """.trimIndent()))

            val response = handler.handle(hwaRequest, null).toMono().block()
            response?.status() shouldBe OK
        }

        scenario("Supports inline interceptor definitions") {
            val context = RoutingContext(
                    plugins = listOf(
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })
                    ),
                    builtinInterceptorsFactory = BuiltinInterceptorsFactory(mapOf("Rewrite" to RewriteInterceptor.Factory()))
            )

            val handler = HttpInterceptorPipeline.Factory().build(listOf("config"),
                    context.get(),
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
                          type: StaticResponseHandler
                          config:
                            status: 200
                            content: hello
                     """.trimIndent()))

            val response = handler.handle(hwaRequest, null).toMono().block()
            response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
        }


        scenario("passes full configuration attribute path (config.config.handler) to the builtins factory") {

            val builtinsFactory = mockk<RoutingObjectFactory> {
                every { build(any(), any()) } returns mockObject()
            }

            val context = RoutingContext(factory = builtinsFactory)

            HttpInterceptorPipeline.Factory().build(
                    listOf("config", "config"),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler:
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                    """.trimIndent()))

            verify {
                builtinsFactory.build(listOf("config", "config", "handler"), any())
            }
        }

    }

    feature("Lifecycle management") {
        scenario("Calls stop() for inlined handler") {
            val childHandler = mockObject()

            val context = RoutingContext(factory = routingObjectFactory(
                    builtins = mapOf("BackendServiceProxy" to HttpHandlerFactory { _, _, _ -> childHandler })
            ))

            val interceptorPipeline = HttpInterceptorPipeline.Factory().build(
                    listOf(),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler:
                          type: BackendServiceProxy
                          config:
                            backendProvider: backendProvider
                    """.trimIndent()))

            interceptorPipeline.stop()

            verify(exactly = 1) { childHandler.stop() }
        }

        scenario("Does not call stop() for referenced handler") {
            val childHandler = mockObject()

            val context = RoutingContext(
                    factory = routingObjectFactory(
                            lookup = routeLookup {
                                ref("handlerRef" to childHandler)
                            }
                    ))

            val interceptorPipeline = HttpInterceptorPipeline.Factory().build(
                    listOf(),
                    context.get(),
                    routingObjectDef("""
                      type: InterceptorPipeline
                      config:
                        handler: handlerRef
                    """.trimIndent()))

            interceptorPipeline.stop()

            verify(exactly = 0) { childHandler.stop() }
        }
    }

})
