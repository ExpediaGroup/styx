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
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.LiveHttpResponse.response
import com.hotels.styx.api.configuration.RouteDatabase
import com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin
import com.hotels.styx.routing.config.BuiltinInterceptorsFactory
import com.hotels.styx.routing.config.HttpHandlerFactory
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.configBlock
import com.hotels.styx.routing.interceptors.RewriteInterceptor
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Mono

class HttpInterceptorPipelineTest : StringSpec({
    val hwaRequest = LiveHttpRequest.get("/x").build()
    val routeDatabase = mockk<RouteDatabase>()

    "it errors when there is a reference to non-existing pipeline" {
        val config = configBlock("""
            config:
              type: InterceptorPipeline
              config:
                pipeline:
                  - non-existing
                handler:
                  name: MyHandler
                  type: Foo
                  config:
                    bar
      """.trimIndent())

        val e = shouldThrow<java.lang.IllegalArgumentException> {
            HttpInterceptorPipeline.Factory(
                    listOf(
                            // Does not accept a scala function literal in place of Java Function1:
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                    ),
                    BuiltinInterceptorsFactory(mapOf()),
                    false
            ).build(listOf("config"), routeDatabase, null, config)
        }

        e.message shouldBe ("No such plugin or interceptor exists, attribute='config.pipeline', name='non-existing'")
    }

    "it errors when handler configuration is missing" {
        val config = configBlock("""
            config:
              type: InterceptorPipeline
              config:
                pipeline:
                  - interceptor2
            """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            HttpInterceptorPipeline.Factory(
                    listOf(
                            namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                            namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) })
                    ),
                    BuiltinInterceptorsFactory(mapOf()),
                    false
            ).build(listOf("config"), routeDatabase, null, config)
        }

        e.message shouldBe ("Routing object definition of type 'InterceptorPipeline', attribute='config', is missing a mandatory 'handler' attribute.")
    }

    "it builds an interceptor pipeline from the configuration" {
        val config = configBlock("""
            config:
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
      """.trimIndent())

        val handler = HttpInterceptorPipeline.Factory(
                listOf(
                        namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                        namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })
                ),
                BuiltinInterceptorsFactory(mapOf()),
                false
        ).build(listOf("config"), routeDatabase, routingObjectFactory(), config)

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
    }

    "it Treats absent 'pipeline' attribute as empty pipeline" {
        val config = configBlock("""
            config:
              type: InterceptorPipeline
              config:
                handler:
                  name: MyHandler
                  type: BackendServiceProxy
                  config:
                    backendProvider: backendProvider
      """.trimIndent())

        val handler = HttpInterceptorPipeline.Factory(listOf(), BuiltinInterceptorsFactory(mapOf()), false).build(listOf("config"), routeDatabase, routingObjectFactory(), config)

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.status() shouldBe (OK)
    }



    "Supports inline interceptor definitions" {
        val config = configBlock("""
            config:
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
      """.trimIndent())

        val handler = HttpInterceptorPipeline.Factory(
                listOf(
                        namedPlugin("interceptor1", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "A").build() }) }),
                        namedPlugin("interceptor2", { request, chain -> chain.proceed(request).map({ response -> response.newBuilder().addHeader("X-Test-Header", "B").build() }) })
                ),
                BuiltinInterceptorsFactory(mapOf("Rewrite" to RewriteInterceptor.Factory())),
                false
        ).build(listOf("config"), routeDatabase, routingObjectFactory(), config)

        val response = Mono.from(handler.handle(hwaRequest, null)).block()
        response?.headers("X-Test-Header") shouldBe (listOf("B", "A"))
    }



    "passes full configuration attribute path (config.config.handler) to the builtins factory" {
        val config = configBlock("""
            config:
              type: InterceptorPipeline
              config:
                handler:
                  name: MyHandler
                  type: BackendServiceProxy
                  config:
                    backendProvider: backendProvider
      """.trimIndent())

        val builtinsFactory = mockk<RoutingObjectFactory>()
        every {
            builtinsFactory.build(any(), any(), any())
        } returns HttpHandler { _, _ -> Eventual.of(response(OK).build()) }

        HttpInterceptorPipeline.Factory(listOf(), null, false)
                .build(listOf("config", "config"), routeDatabase, builtinsFactory, config)

        verify {
            builtinsFactory.build(listOf("config", "config", "handler"), any(), any())
        }
    }


})


fun mockHandlerFactory(): HttpHandlerFactory {
    val handlerFactory = mockk<HttpHandlerFactory>()

    every {
        handlerFactory.build(any(), any(), any(), any())
    } returns HttpHandler{ _, _ -> Eventual.of(LiveHttpResponse.response(OK).build()) }

    return handlerFactory
}

fun routingObjectFactory() = RoutingObjectFactory(mapOf("BackendServiceProxy" to mockHandlerFactory()))
