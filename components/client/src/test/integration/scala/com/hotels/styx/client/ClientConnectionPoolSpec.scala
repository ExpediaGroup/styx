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

import java.lang

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpRequest.Builder
import com.hotels.styx.api.client.{ActiveOrigins, ConnectionPool, Origin, RemoteHost}
import com.hotels.styx.api.messages.HttpResponseStatus.OK
import com.hotels.styx.api.metrics.MetricRegistry
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry
import com.hotels.styx.client.OriginsInventory.RemoteHostWrapper
import com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.client.connectionpool.ConnectionPools
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import org.scalatest._
import org.scalatest.concurrent.Eventually

import scala.collection.JavaConverters._

class ClientConnectionPoolSpec extends FunSuite with BeforeAndAfterAll with Eventually with ShouldMatchers with Matchers with OriginSupport {

  var metricRegistry: CodaHaleMetricRegistry = _

  var client: StyxHttpClient = _

  val (originOne, originServer) = originAndWireMockServer("webapp", "webapp-01")


  override protected def beforeAll(): Unit = {
    WireMock.configureFor("localhost", originOne.host.getPort)
    stubFor(WireMock.get(urlEqualTo("/foo")).willReturn(aResponse.withStatus(200)))

    metricRegistry = new CodaHaleMetricRegistry()

    val backendService = new BackendService.Builder().origins(originOne).build()

    client = newHttpClientBuilder(backendService)
      .metricsRegistry(metricRegistry)
        .loadBalancingStrategy(roundRobinStrategy(activeOrigins(backendService)))
      .build
  }

  override protected def afterAll(): Unit = {
    originServer.stop()
  }

  def activeOrigins(backendService: BackendService): ActiveOrigins = {
    new ActiveOrigins {
      /**
        * Returns the list of the origins ready to accept traffic.
        *
        * @return a list of connection pools for each active origin
        */
      override def snapshot(): lang.Iterable[RemoteHost] = backendService.origins().asScala
        .map(origin => new RemoteHostWrapper(ConnectionPools.poolForOrigin(origin, new CodaHaleMetricRegistry, backendService.responseTimeoutMillis())).asInstanceOf[RemoteHost])
        .asJava
    }
  }

  def roundRobinStrategy(activeOrigins: ActiveOrigins): RoundRobinStrategy = new RoundRobinStrategy(activeOrigins)

  def stickySessionStrategy(activeOrigins: ActiveOrigins) = new StickySessionLoadBalancingStrategy(activeOrigins, roundRobinStrategy(activeOrigins))


  // TODO: Mikko this is a pseudo-IT test. Should be in the styx server e2e suite because
  // it is testing mainly for DI and wiring rather than the client functionality itself.
  ignore("Removes connections from pool when they terminate.") {
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
