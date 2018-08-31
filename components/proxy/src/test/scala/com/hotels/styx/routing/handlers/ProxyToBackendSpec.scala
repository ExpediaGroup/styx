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

import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.Environment
import com.hotels.styx.api.Id.id
import com.hotels.styx.api._
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.{OriginStatsFactory, OriginsInventory}
import com.hotels.styx.common.StyxFutures
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.BackendServiceClientFactory
import com.hotels.styx.routing.config.RouteHandlerDefinition
import com.hotels.styx.server.HttpInterceptorContext
import org.scalatest.{FunSpec, ShouldMatchers}
import rx.Observable

import scala.collection.JavaConversions._

class ProxyToBackendSpec extends FunSpec with ShouldMatchers {

  val environment = new Environment.Builder().build()

  private val config = configBlock(
    """
      |config:
      |    name: ProxyToBackend
      |    type: ProxyToBackend
      |    config:
      |      backend:
      |        id: "ba"
      |        connectionPool:
      |          maxConnectionsPerHost: 45
      |          maxPendingConnectionsPerHost: 15
      |        responseTimeoutMillis: 60000
      |        origins:
      |        - { id: "ba1", host: "localhost:9094" }
      |
      |""".stripMargin)

  it("builds ProxyToBackend handler") {
    val handler = new ProxyToBackend.ConfigFactory(environment, clientFactory()).build(List(), null, config)

    val response = StyxFutures.await(handler.handle(HttpRequest.get("/foo").build(), HttpInterceptorContext.create).asCompletableFuture())
    response.status should be (OK)
  }

  it("throws for missing mandatory 'backend' attribute") {
    val config = configBlock(
      """
        |config:
        |    name: myProxy
        |    type: ProxyToBackend
        |    config:
        |      na: na
        |""".stripMargin)

    val e = intercept[IllegalArgumentException] {
      val handler = new ProxyToBackend.ConfigFactory(environment, clientFactory())
              .build(List("config", "config"), null, config)
    }

    e.getMessage should be("Routing object definition of type 'ProxyToBackend', attribute='config.config', is missing a mandatory 'backend' attribute.")
  }

  it("throws for a missing mandatory backend.origins attribute") {
    val config = configBlock(
      """
        |config:
        |    name: ProxyToBackend
        |    type: ProxyToBackend
        |    config:
        |      backend:
        |        id: "ba"
        |        connectionPool:
        |          maxConnectionsPerHost: 45
        |          maxPendingConnectionsPerHost: 15
        |        responseTimeoutMillis: 60000
        |""".stripMargin)

    val e = intercept[IllegalArgumentException] {
      val handler = new ProxyToBackend.ConfigFactory(environment, clientFactory())
              .build(List("config", "config"), null, config)
    }

    e.getMessage should be("Routing object definition of type 'ProxyToBackend', attribute='config.config.backend', is missing a mandatory 'origins' attribute.")
  }

  private def configBlock(text: String) = new YamlConfig(text).get("config", classOf[RouteHandlerDefinition]).get()

  private def clientFactory() = new BackendServiceClientFactory() {
    override def createClient(backendService: BackendService, originsInventory: OriginsInventory, originStatsFactory: OriginStatsFactory): HttpClient = new HttpClient {
      override def sendRequest(request: HttpRequest): Observable[HttpResponse] = {
        backendService.id() should be (id("ba"))
        backendService.connectionPoolConfig().maxConnectionsPerHost() should be (45)
        backendService.connectionPoolConfig().maxPendingConnectionsPerHost() should be (15)
        backendService.responseTimeoutMillis() should be (60000)
        backendService.origins().head.id() should be(id("ba1"))
        backendService.origins().head.host().getPort should be(9094)
        Observable
          .just(HttpResponse
            .response(OK)
            .addHeader("X-Backend-Service", backendService.id())
            .build()
          )
      }
    }
  }

}
