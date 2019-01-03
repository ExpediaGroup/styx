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
package com.hotels.styx.server

import java.nio.charset.StandardCharsets.UTF_8
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

import com.hotels.styx.StyxProxySpec
import com.hotels.styx.api.{HttpRequest, HttpResponse}
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.exceptions.TransportLostException
import com.hotels.styx.client.BadHttpResponseException
import com.hotels.styx.support.TestClientSupport
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, ProxyConfig, StyxConfig}
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._
import scala.concurrent.ExecutionException
import scala.util.{Failure, Success, Try}

class ServerConnectionsSpec extends FunSpec
  with StyxProxySpec
  with TestClientSupport
  with Eventually {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(ProxyConfig(maxConnectionsCount = 5))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/" -> HttpBackend("myapp", Origins(normalBackend))
    )
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
  }

  describe("Ensuring connection metrics are accurate") {
    it("should record zero total-connections after finishing responses, including when connections are rejected due to exceeeding the configured maximum") {
      val request: HttpRequest = get("http://localhost:" + styxServer.proxyHttpAddress().getPort + "/").build()

      val futures = new util.ArrayList[CompletableFuture[HttpResponse]]()

      for (_ <- 1 to 10) {
        futures.add(client.send(request))
      }

      // We expect exceptions because we are trying to establish more connections than we have configured the server to allow
      futures.forEach(future => {
        ignoreExpectedExceptions(() => future.get(1, SECONDS))
      })

      eventually(timeout(1 second)) {
        getTotalConnectionsMetric.bodyAs(UTF_8) should be("{\"connections.total-connections\":{\"count\":0}}")
      }
    }
  }

  private def ignoreExpectedExceptions(fun: () => Unit): Unit = {
    val outcome = Try(fun()) recoverWith {
      case ee : ExecutionException => Failure(ee.getCause)
      case e => Failure(e)
    } recoverWith {
      case bhre : BadHttpResponseException => Success("ignore this: " + bhre)
      case tle: TransportLostException => Success("ignore this: " + tle)
      case e => Failure(e)
    }

    // when the connection is rejected we expect to end up with Success("ignore this: BadHttpResponseException ....")

    outcome.get
  }

  private def getTotalConnectionsMetric = {
    val request: HttpRequest = get("http://localhost:" + styxServer.adminHttpAddress().getPort + "/admin/metrics/connections.total-connections").build()

    client.send(request).get(1, SECONDS)
  }
}


