/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.routing.handlers

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

import com.hotels.styx.Environment
import com.hotels.styx.api.client.Origin.newOriginBuilder
import com.hotels.styx.api.{HttpClient, HttpHandler2, HttpRequest, HttpResponse}
import com.hotels.styx.client.OriginsInventory
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.infrastructure.Registry.ReloadResult.reloaded
import com.hotels.styx.infrastructure.Registry.{Changes, ReloadResult}
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.infrastructure.{AbstractRegistry, Registry}
import com.hotels.styx.proxy.{BackendServiceClientFactory, BackendServicesRouter}
import com.hotels.styx.proxy.backends.CommonBackendServiceRegistry
import com.hotels.styx.proxy.backends.CommonBackendServiceRegistry.StyxBackendService
import com.hotels.styx.routing.config.RouteHandlerDefinition
import io.netty.handler.codec.http.HttpResponseStatus
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, ShouldMatchers}
import rx.Observable
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.Matchers.{eq => meq}
import org.mockito.Matchers.any

import scala.collection.JavaConversions._

class BackendServiceProxySpec extends FunSpec with ShouldMatchers with MockitoSugar {

  val hwaRequest = HttpRequest.Builder.get("/x").build()
  val laRequest = HttpRequest.Builder.get("/lp/x").build()
  val baRequest = HttpRequest.Builder.get("/ba/x").build()

  val environment = new Environment.Builder().build()

  it("builds a backend service proxy from the configuration ") {
    val config = configBlock(
      """
        |config:
        |  type: BackendServiceProxy
        |  config:
        |    backendProvider: backendServicesRegistry
      """.stripMargin)

    val registry = mock[CommonBackendServiceRegistry]
    val registries = mock[java.util.Map[String, CommonBackendServiceRegistry]]
    when(registries.get(meq("backendServicesRegistry"))).thenReturn(registry)

    val handler = new BackendServiceProxy.ConfigFactory(registries).build(List(), null, config)

    verify(registries).get(meq("backendServicesRegistry"))
    verify(registry).addListener(any(classOf[BackendServicesRouter]))
  }

//  it ("errors when backendProvider attribute is not specified") {
//    val config = configBlock(
//      """
//        |config:
//        |  type: BackendServiceProxy
//        |  config:
//        |    foo: bar
//      """.stripMargin)
//
//    val registries: Map[String, Registry[BackendService]] = Map.empty
//
//    val e = intercept[IllegalArgumentException] {
//      val handler = new BackendServiceProxy.ConfigFactory(environment, clientFactory(), registries).build(List("config", "config"), null, config)
//    }
//    e.getMessage should be ("Routing object definition of type 'BackendServiceProxy', attribute='config.config', is missing a mandatory 'backendProvider' attribute.")
//  }
//
//  it ("errors when backendProvider does not exists") {
//    val config = configBlock(
//      """
//        |config:
//        |  type: BackendServiceProxy
//        |  config:
//        |    backendProvider: bar
//      """.stripMargin)
//
//    val e = intercept[IllegalArgumentException] {
//      val registries: Map[String, Registry[BackendService]] = Map.empty
//      val handler = new BackendServiceProxy.ConfigFactory(environment, clientFactory(), registries).build(List("config", "config"), null, config)
//    }
//    e.getMessage should be ("No such backend service provider exists, attribute='config.config.backendProvider', name='bar'")
//  }

  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RouteHandlerDefinition]).get()

  private def clientFactory() = new BackendServiceClientFactory() {
    override def createClient(backendService: BackendService, originsInventory: OriginsInventory): HttpClient = new HttpClient {
      override def sendRequest(request: HttpRequest): Observable[HttpResponse] = Observable
        .just(HttpResponse.Builder
          .response(HttpResponseStatus.OK)
          .addHeader("X-Backend-Service", backendService.id())
          .build()
        )
    }
  }

  def registry(backends: StyxBackendService*) = new AbstractRegistry[StyxBackendService]() {
    override def reload(): CompletableFuture[ReloadResult] = {
      notifyListeners(
        new Changes.Builder[StyxBackendService]()
          .added(backends:_*)
          .build())
      completedFuture(reloaded("ok"))
    }
  }

}
