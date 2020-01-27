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

import com.hotels.styx.Environment
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.spi.AbstractRegistry
import com.hotels.styx.api.extension.service.spi.Registry
import com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded
import com.hotels.styx.client.BackendServiceClient
import com.hotels.styx.proxy.BackendServiceClientFactory
import com.hotels.styx.RoutingObjectFactoryContext
import com.hotels.styx.requestContext
import com.hotels.styx.routingObjectDef
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class BackendServiceProxyTest : StringSpec({

    val hwaRequest = LiveHttpRequest.get("/x").build()
    val laRequest = LiveHttpRequest.get("/lp/x").build()
    val baRequest = LiveHttpRequest.get("/ba/x").build()

    val environment = Environment.Builder().build()
    val context = RoutingObjectFactoryContext(environment = environment).get()


    "builds a backend service proxy from the configuration " {
        val config = routingObjectDef("""
                  type: BackendServiceProxy
                  config:
                    backendProvider: backendServicesRegistry
              """.trimIndent())

        val backendRegistry = registry(
                 BackendService.Builder().id("hwa").origins(newOriginBuilder("localhost", 0).build()).path("/").build(),
                 BackendService.Builder().id("la").origins(newOriginBuilder("localhost", 1).build()).path("/lp/x").build(),
                 BackendService.Builder().id("ba").origins(newOriginBuilder("localhost", 2).build()).path("/ba/x").build())

        val services = mapOf("backendServicesRegistry" to backendRegistry)

        val handler = BackendServiceProxy.Factory(environment, clientFactory(), services).build(listOf(), context, config)
        backendRegistry.reload()

        handler.handle(hwaRequest, requestContext())
                .toMono()
                .block()!!
                .header("X-Backend-Service").get() shouldBe("hwa")

        handler.handle(laRequest, requestContext())
                .toMono()
                .block()!!
                .header("X-Backend-Service").get() shouldBe("la")

        handler.handle(baRequest, requestContext())
                .toMono()
                .block()!!
                .header("X-Backend-Service").get() shouldBe("ba")
    }

    "errors when backendProvider attribute is not specified" {
        val config = routingObjectDef("""
                  type: BackendServiceProxy
                  config:
                    foo: bar
              """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            BackendServiceProxy.Factory(environment, clientFactory(), mapOf()).build(listOf("config", "config"), context,  config)
        }
        e.message shouldBe("Routing object definition of type 'BackendServiceProxy', attribute='config.config', is missing a mandatory 'backendProvider' attribute.")
    }

    "errors when backendProvider does not exists" {
        val config = routingObjectDef("""
                  type: BackendServiceProxy
                  config:
                    backendProvider: bar
              """.trimIndent())

        val e = shouldThrow<IllegalArgumentException> {
            BackendServiceProxy.Factory(environment, clientFactory(), mapOf()).build(listOf("config", "config"), context, config)
        }
        e.message shouldBe("No such backend service provider exists, attribute='config.config.backendProvider', name='bar'")
    }

})

private fun clientFactory(): BackendServiceClientFactory = BackendServiceClientFactory {
        backendService, originsInventory, originStatsFactory ->
            BackendServiceClient {
                request, context -> Mono.just(
                        LiveHttpResponse
                                .response(OK)
                                .addHeader("X-Backend-Service", backendService.id())
                                .build())
            }
        }

private fun registry(vararg backends: BackendService)= object: AbstractRegistry<BackendService>() {
        override fun reload(): CompletableFuture<Registry.ReloadResult> {
            notifyListeners(
                    Registry.Changes.Builder<BackendService>()
                            .added(backends.asIterable())
                            .build())
            return completedFuture(reloaded("ok"))
        }
    }
