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

import ch.qos.logback.classic.Level._
import com.github.tomakehurst.wiremock.client.WireMock.{get => _, _}
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.support.matchers.LoggingEventMatcher._
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.support.server.UrlMatchingStrategies._
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._
import com.hotels.styx.api.HttpResponseStatus.OK
import org.hamcrest.MatcherAssert._
import org.hamcrest.Matchers._
import org.scalatest.FunSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class HttpMessageLoggingSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with Eventually {

  val crtFile = fixturesHome(this.getClass, "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(this.getClass, "/ssl/testCredentials.key").toString

  override val styxConfig = StyxConfig(
    ProxyConfig(
      Connectors(
        HttpConnectorConfig(),
        HttpsConnectorConfig(
          cipherSuites = Seq("TLS_RSA_WITH_AES_128_GCM_SHA256"),
          certificateFile = crtFile,
          certificateKeyFile = keyFile))
    ),
    yamlText = "" +
      "request-logging:\n" +
      "  inbound:\n" +
      "    enabled: true\n" +
      "    longFormat: true\n"
  )

  val mockServer = FakeHttpServer.HttpStartupConfig()
    .start()
    .stub(urlStartingWith("/foobar"), aResponse
      .withStatus(OK.code())
      .withHeader(TRANSFER_ENCODING, CHUNKED)
      .withBody("I should be here!")
    )

  var logger: LoggingTestSupport = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/foobar" -> HttpBackend("appOne", Origins(mockServer), responseTimeout = 5.seconds)
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

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    logger = new LoggingTestSupport("com.hotels.styx.http-messages.inbound")
  }

  override protected def afterEach(): Unit = {
    logger.stop()
    super.afterEach()
  }

  describe("Styx request/response logging") {
    it("Should log request and response") {
      val request = get(styxServer.routerURL("/foobar"))
        .build()

      val resp = decodedRequest(request)

      assertThat(resp.status(), is(OK))

      eventually(timeout(3.seconds)) {
        assertThat(logger.log.size(), is(2))

        assertThat(logger.log(), hasItem(loggingEvent(INFO,
          "requestId=[-a-z0-9]+, request=\\{method=GET, secure=false, uri=http://localhost:[0-9]+/foobar, origin=\"N/A\", headers=\\[Host=localhost:[0-9]+\\]}")))

        assertThat(logger.log(), hasItem(loggingEvent(INFO,
          "requestId=[-a-z0-9]+, response=\\{status=200 OK, headers=\\[Server=Jetty\\(6.1.26\\), " + ORIGIN_ID_DEFAULT + "=generic-app-01, Via=1.1 styx\\]\\}")))
      }
    }

    it("Should log HTTPS request") {
      val request = get(styxServer.secureRouterURL("/foobar")).build()

      val resp = decodedRequest(request)

      assertThat(resp.status(), is(OK))

      eventually(timeout(3.seconds)) {
        assertThat(logger.log.size(), is(2))

        assertThat(logger.log(), hasItem(loggingEvent(INFO,
          "requestId=[-a-z0-9]+, request=\\{method=GET, secure=true, uri=https://localhost:[0-9]+/foobar, origin=\"N/A\", headers=.*}")))

        assertThat(logger.log(), hasItem(loggingEvent(INFO,
          "requestId=[-a-z0-9]+, response=\\{status=200 OK, headers=\\[Server=Jetty\\(6.1.26\\), " + ORIGIN_ID_DEFAULT + "=generic-app-01, Via=1.1 styx\\]\\}")))
      }
    }
  }
}
