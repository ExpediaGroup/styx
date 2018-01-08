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
package com.hotels.styx.client

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.api.HttpRequest.Builder
import com.hotels.styx.api.client.Origin
import com.hotels.styx.api.metrics.MetricRegistry
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.api.messages.HttpResponseStatusCodes.OK
import org.scalatest._
import org.scalatest.concurrent.Eventually

class ClientConnectionPoolSpec extends FunSuite with BeforeAndAfterAll with Eventually with ShouldMatchers with Matchers with OriginSupport {

  var metricRegistry: CodaHaleMetricRegistry = _

  var client: StyxHttpClient = _

  val (originOne, originServer) = originAndWireMockServer("webapp", "webapp-01")


  override protected def beforeAll(): Unit = {
    WireMock.configureFor("localhost", originOne.host.getPort)
    stubFor(WireMock.get(urlEqualTo("/foo")).willReturn(aResponse.withStatus(200)))

    metricRegistry = new CodaHaleMetricRegistry()

    val backendService = new BackendService.Builder().origins(originOne).build()

    val originsInventory = newOriginsInventoryBuilder(backendService)
      .metricsRegistry(metricRegistry)
      .build()

    client = newHttpClientBuilder(backendService)
      .metricsRegistry(metricRegistry)
      .originsInventory(originsInventory)
      .build
  }

  override protected def afterAll(): Unit = {
    originServer.stop()
  }

  test("Removes connections from pool when they terminate.") {
    waitForResponse(client.sendRequest(get("/foo"))).status() should be(OK)

    eventually {
      busyConnections should be(0)
    }

    terminateConnections()

    eventually {
      availableConnections should be(0)
      busyConnections should be(0)
    }
  }

  def terminateConnections() {
    originServer.stop()
  }

  def busyConnections: Any = {
    connectionsPoolGauge(metricRegistry, originOne, "busy-connections")
  }

  def availableConnections: Any = {
    connectionsPoolGauge(metricRegistry, originOne, "available-connections")
  }

  def get(url: String) = Builder.get(url)
    .header("Host", "localhost:%d".format(originOne.host().getPort))
    .build()

  def poolMetricName(origin: Origin, name: String) = "origins.%s.%s.connectionspool.%s"
    .format(origin.applicationId(), origin.id(), name)

  def connectionsPoolGauge(registry: MetricRegistry, origin: Origin, name: String) =
    registry.getGauges.get(poolMetricName(origin, name)).getValue
}
