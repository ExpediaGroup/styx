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
package com.hotels.styx.proxy

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post => wmpost}
import com.hotels.styx.api.FullHttpRequest.post
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{DefaultStyxConfiguration, StyxClientSupplier, StyxProxySpec}
import org.scalatest.FunSpec

import scala.concurrent.duration._

class OriginsConnectionSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration
  with StyxClientSupplier {

  val mockServer = FakeHttpServer.HttpStartupConfig().start()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds)
    )
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  describe("Origins closing connections after responses") {
    it("Styx doesn't propagate TransportLostException") {
      mockServer.stub(wmpost(urlStartingWith("/foobar")), aResponse
        .withStatus(OK.code())
      )

      val loggingTestSupport: LoggingTestSupport = new LoggingTestSupport(classOf[StyxHttpClient])

      for (i <- 1 to 5) {
        val request = post(styxServer.routerURL("/foobar"))
          .addHeader("Content-Length", "0")
          .build()

        val response = decodedRequest(request)

        response.status() should be(OK)
        response.bodyAs(UTF_8) should be("")
      }

      Thread.sleep(6000)
      loggingTestSupport.log().isEmpty should be(true)
    }

    it("Styx doesn't propagate TransportLostException for bodiless responses") {
      mockServer.stub(wmpost(urlStartingWith("/foobar")), aResponse
        .withStatus(204)
      )

      val loggingTestSupport: LoggingTestSupport = new LoggingTestSupport(classOf[StyxHttpClient])

      for (i <- 1 to 5) {
        val request = post(styxServer.routerURL("/foobar"))
          .addHeader("Content-Length", "0")
          .build()

        val response = decodedRequest(request)

        response.status() should be(NO_CONTENT)
        response.bodyAs(UTF_8) should be("")
      }

      Thread.sleep(6000)
      loggingTestSupport.log().isEmpty should be(true)
    }
  }

}
