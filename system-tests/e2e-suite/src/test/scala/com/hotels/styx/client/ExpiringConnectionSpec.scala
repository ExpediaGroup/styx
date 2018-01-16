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

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock.{get => _, _}
import com.hotels.styx.api.HttpRequest.Builder._
import com.hotels.styx.api.messages.HttpResponseStatus.OK
import com.hotels.styx.support.api.BlockingObservables.waitForResponse
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{ConnectionPoolSettings, HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import org.hamcrest.MatcherAssert._
import org.hamcrest.Matchers._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._
class ExpiringConnectionSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with Eventually {

  val mockServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .stub(urlStartingWith("/foobar"), aResponse
      .withStatus(200)
      .withHeader(TRANSFER_ENCODING, CHUNKED)
      .withBody("I should be here!")
    )

  override val styxConfig = StyxConfig()

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds,
        connectionPoolConfig = ConnectionPoolSettings(connectionExpirationSeconds = 1L))
    )

    val request = get(s"http://localhost:${mockServer.port()}/foobar").build()
    val resp = decodedRequest(request)
    resp.status() should be (OK)
    resp.bodyAs(UTF_8) should be ("I should be here!")
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  describe("Styx connection pool expiration policy") {
    it("Should reuse connection after before 1 second passes") {
      val request = get(styxServer.routerURL("/foobar"))
        .build()

      val response1 = waitForResponse(client.sendRequest(request))

      assertThat(response1.status(), is(OK))

      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-closed").get should be(0)

      val response2 = waitForResponse(client.sendRequest(request))


      eventually(timeout(3.seconds)) {
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-closed").get should be(0)
      }
    }

    it("Should expire connection after 1 second") {
      val request = get(styxServer.routerURL("/foobar"))
        .build()

      val response1 = waitForResponse(client.sendRequest(request))

      assertThat(response1.status(), is(OK))

      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
      styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-closed").get should be(0)

      Thread.sleep(1000)

      val response2 = waitForResponse(client.sendRequest(request))


      eventually(timeout(2.seconds)) {
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.available-connections").get should be(1)
        styxServer.metricsSnapshot.gauge(s"origins.appOne.generic-app-01.connectionspool.connections-closed").get should be(1)
      }
    }
  }
}
