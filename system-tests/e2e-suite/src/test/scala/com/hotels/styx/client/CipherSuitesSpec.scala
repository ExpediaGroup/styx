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
package com.hotels.styx.client

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ValueMatchingStrategy, WireMock}
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, SequentialNestedSuiteExecution}


class CipherSuitesSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution {

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")

  override val styxConfig = StyxYamlConfig(
    """
      |proxy:
      |  connectors:
      |    http:
      |      port: 0
      |admin:
      |  connectors:
      |    http:
      |      port: 0
      |request-logging:
      |  enabled: true
    """.stripMargin,
    logbackXmlLocation = logback)

    val originA = FakeHttpServer.HttpsStartupConfig(
      appId = "appA",
      originId = "appA-01",
      sslProvider = "JDK",
      cipherSuites = Seq("TLS_RSA_WITH_AES_128_CBC_SHA")
    )
      .start()
      .stub(WireMock.get(urlMatching("/.*")), originResponse("appA-01"))

  val originB = FakeHttpServer.HttpsStartupConfig(
    appId = "appB",
    originId = "appB-01",
    sslProvider = "JDK",
    cipherSuites = Seq("TLS_RSA_WITH_AES_128_CBC_SHA")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("appB-01"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
            "/compatible/" -> HttpsBackend(
              "appA",
              Origins(originA),
              TlsSettings(
                authenticate = false,
                sslProvider = "JDK",
                cipherSuites = Seq("TLS_RSA_WITH_AES_128_CBC_SHA")
              )),
      "/nonCompatible/" -> HttpsBackend(
        "appB",
        Origins(originB),
        TlsSettings(
          authenticate = false,
          sslProvider = "JDK",
          cipherSuites = Seq("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256")
        ))
    )
  }

  override protected def afterAll(): Unit = {
    originA.stop()
    originB.stop()
    super.afterAll()
  }

  describe("Cipher Suite Selection for Backends") {

    it("Proxies to a TLS origin with compatible cipher suites.") {
      val response = decodedRequest(httpRequest("/compatible/a"))
      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      originA.verify(
        getRequestedFor(
          urlEqualTo("/compatible/a"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))
    }

    it("Fails to handshake with an origin with incompatible cipher suite.") {
      val response = decodedRequest(httpRequest("/nonCompatible/b"))
      assert(response.status().code() == 502)
      assert(response.bodyAs(UTF_8) == "Site temporarily unavailable.")

      originB.verify(0, getRequestedFor(urlEqualTo("/nonCompatible/b")))
    }
  }

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches(matches)
    matchingStrategy
  }

  def originResponse(appId: String) = aResponse
    .withStatus(200)
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

}
