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
package com.hotels.styx.client

import com.github.tomakehurst.wiremock.client.WireMock.{get => _, _}
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.{HttpHeaderNames, HttpHeaderValues, HttpResponse}
import com.hotels.styx.api.LiveHttpRequest.get
import com.hotels.styx.api.extension.ActiveOrigins
import com.hotels.styx.api.extension.Origin.newOriginBuilder
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder
import com.hotels.styx.client.StyxBackendServiceClient.newHttpClientBuilder
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.hamcrest.MatcherAssert._
import org.hamcrest.Matchers._
import org.scalatest.{FixtureContext, FunSpec, Succeeded}
import org.scalatest.concurrent.Eventually
import reactor.core.publisher.Mono
import com.hotels.styx.support.Support.requestContext

import scala.concurrent.duration._

class ExpiringConnectionSpec extends FunSpec
  with DefaultStyxConfiguration
  with StyxProxySpec
  with Eventually {

  override lazy val styxConfig = StyxConfig(
    yamlText =
      """
        |request-logging:
        |  inbound:
        |    enabled: True
        |    longFormat: True
        |  outbound:
        |    enabled: True
        |    longFormat: True
        |""".stripMargin
  )

  val mockServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .stub(urlStartingWith("/app1"), aResponse
      .withStatus(200)
    )

  var pooledClient: StyxBackendServiceClient = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/app1" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds,
        connectionPoolConfig = ConnectionPoolSettings(connectionExpirationSeconds = 1L))
    )

    val backendService = new BackendService.Builder()
      .origins(newOriginBuilder("localhost", styxServer.httpPort).build())
      .build()

    pooledClient = newHttpClientBuilder(backendService.id)
      .loadBalancer(roundRobinStrategy(activeOrigins(backendService)))
      .build
  }

  override protected def afterEach(): Unit = {
    println("Metrics from ExpiringConnectionSpec: \n" + styxServer.metricsSnapshot)
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  it("Should expire connection after 1 second") {

    // Prime a connection:
    val response1: HttpResponse = Mono.from(pooledClient.sendRequest(
      get(styxServer.routerURL("/app1/1"))
        .header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        .build(),
      requestContext()))
      .flatMap(r => Mono.from(r.aggregate(1024)))
      .block()

    assertThat(response1.status(), is(OK))

    // Ensure that a connection got created in pool:
    eventually(timeout(1.seconds)) {
      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-closed").get should be(0)
    }

    Thread.sleep(1000)

    // Send a second request. The connection would have been expired after two seconds
    // Therefore, the pool creates a new connection.
    val response2: HttpResponse = Mono.from(pooledClient.sendRequest(
      get(styxServer.routerURL("/app1/2"))
        .header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        .build(),
      requestContext()))
      .flatMap(r => Mono.from(r.aggregate(1024)))
      .block()

    assertThat(response2.status(), is(OK))

    eventually(timeout(2.seconds)) {
      withClue("A connection should be available in pool") {
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
      }

      withClue("A previous connection should have been terminated") {
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-terminated").get should be(1)
      }
    }
  }

  def activeOrigins(backendService: BackendService): ActiveOrigins = newOriginsInventoryBuilder(backendService).build()

  def roundRobinStrategy(activeOrigins: ActiveOrigins): RoundRobinStrategy = new RoundRobinStrategy(activeOrigins, activeOrigins.snapshot())
}
