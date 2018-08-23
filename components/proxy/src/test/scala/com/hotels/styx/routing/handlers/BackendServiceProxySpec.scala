/*
  Copyright (C) 2013-2018 Expedia Inc.

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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

import com.hotels.styx.Environment
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.spi.{AbstractRegistry, Registry}
import com.hotels.styx.api.extension.service.spi.{AbstractRegistry, Registry}
import com.hotels.styx.api.{HttpClient, HttpRequest, HttpResponse, HttpResponseStatus}
import com.hotels.styx.client.{OriginStatsFactory, OriginsInventory}
import com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded
import com.hotels.styx.api.extension.service.spi.Registry.{Changes, ReloadResult}
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.BackendServiceClientFactory
import com.hotels.styx.routing.config.RouteHandlerDefinition
import com.hotels.styx.server.HttpInterceptorContext
import com.hotels.styx.support.api.BlockingObservables
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}
import rx.Observable

import scala.collection.JavaConversions._

class BackendServiceProxySpec extends FunSpec with ShouldMatchers with MockitoSugar {

  val hwaRequest = HttpRequest.get("/x").build()
  val laRequest = HttpRequest.get("/lp/x").build()
  val baRequest = HttpRequest.get("/ba/x").build()

  val environment = new Environment.Builder().build()

  it("builds a backend service proxy from the configuration ") {
    val config = configBlock(
      """
        |config:
        |  type: BackendServiceProxy
        |  config:
        |    backendProvider: backendServicesRegistry
      """.stripMargin)

    val backendRegistry = registry(
      new BackendService.Builder().id("hwa").origins(newOriginBuilder("localhost", 0).build()).path("/").build(),
      new BackendService.Builder().id("la").origins(newOriginBuilder("localhost", 1).build()).path("/lp/x").build(),
      new BackendService.Builder().id("ba").origins(newOriginBuilder("localhost", 2).build()).path("/ba/x").build())

    val services: Map[String, Registry[BackendService]] = Map("backendServicesRegistry" -> backendRegistry)

    val handler = new BackendServiceProxy.ConfigFactory(environment, clientFactory(), services).build(List(), null, config)
    backendRegistry.reload()

    val hwaResponse = StyxFutures.await(handler.handle(hwaRequest, HttpInterceptorContext.create).asCompletableFuture())
    hwaResponse.header("X-Backend-Service").get() should be("hwa")

    val laResponse = StyxFutures.await(handler.handle(laRequest, HttpInterceptorContext.create).asCompletableFuture())
    laResponse.header("X-Backend-Service").get() should be("la")

    val baResponse = StyxFutures.await(handler.handle(baRequest, HttpInterceptorContext.create).asCompletableFuture())
    baResponse.header("X-Backend-Service").get() should be("ba")
  }

  it ("errors when backendProvider attribute is not specified") {
    val config = configBlock(
      """
        |config:
        |  type: BackendServiceProxy
        |  config:
        |    foo: bar
      """.stripMargin)

    val registries: Map[String, Registry[BackendService]] = Map.empty

    val e = intercept[IllegalArgumentException] {
      val handler = new BackendServiceProxy.ConfigFactory(environment, clientFactory(), registries).build(List("config", "config"), null, config)
    }
    e.getMessage should be ("Routing object definition of type 'BackendServiceProxy', attribute='config.config', is missing a mandatory 'backendProvider' attribute.")
  }

  it ("errors when backendProvider does not exists") {
    val config = configBlock(
      """
        |config:
        |  type: BackendServiceProxy
        |  config:
        |    backendProvider: bar
      """.stripMargin)

    val e = intercept[IllegalArgumentException] {
      val registries: Map[String, Registry[BackendService]] = Map.empty
      val handler = new BackendServiceProxy.ConfigFactory(environment, clientFactory(), registries).build(List("config", "config"), null, config)
    }
    e.getMessage should be ("No such backend service provider exists, attribute='config.config.backendProvider', name='bar'")
  }

  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RouteHandlerDefinition]).get()

  private def clientFactory() = new BackendServiceClientFactory() {
    override def createClient(backendService: BackendService, originsInventory: OriginsInventory, originStatsFactory: OriginStatsFactory): HttpClient = new HttpClient {
      override def sendRequest(request: HttpRequest): Observable[HttpResponse] = Observable
        .just(HttpResponse
          .response(HttpResponseStatus.OK)
          .addHeader("X-Backend-Service", backendService.id())
          .build()
        )
    }
  }

  def registry(backends: BackendService*) = new AbstractRegistry[BackendService]() {
    override def reload(): CompletableFuture[ReloadResult] = {
      notifyListeners(
        new Changes.Builder[BackendService]()
          .added(backends:_*)
          .build())
      completedFuture(reloaded("ok"))
    }
  }

}
