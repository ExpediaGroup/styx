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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ValueMatchingStrategy, WireMock}
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, SequentialNestedSuiteExecution}
import java.nio.charset.StandardCharsets.UTF_8

class TlsVersionSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution {

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")

  val appOriginTlsv11 = FakeHttpServer.HttpsStartupConfig(
    appId = "appTls11",
    originId = "appTls11-01",
    protocols = Seq("TLSv1.1")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.1"))

  val appOriginTlsv12B = FakeHttpServer.HttpsStartupConfig(
    appId = "appTls11B",
    originId = "appTls11B-01",
    protocols = Seq("TLSv1.2")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.2 B"))

  val appOriginTlsDefault = FakeHttpServer.HttpsStartupConfig(
    appId = "appTlsDefault",
    originId = "appTlsDefault-01",
    protocols = Seq("TLSv1.1", "TLSv1.2")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.1"))

  val appOriginTlsv12 = FakeHttpServer.HttpsStartupConfig(
    appId = "appTls12",
    originId = "appTls12-02",
    protocols = Seq("TLSv1.2")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.2"))

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

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/tls11/" -> HttpsBackend(
        "appTls11",
        Origins(appOriginTlsv11),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.1"))),

      "/tlsDefault/" -> HttpsBackend(
        "appTlsDefault",
        Origins(appOriginTlsDefault),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.1", "TLSv1.2"))),

      "/tls12" -> HttpsBackend(
        "appTls12",
        Origins(appOriginTlsv12),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.2"))),

      "/tls11-to-tls12" -> HttpsBackend(
        "appTls11B",
        Origins(appOriginTlsv12B),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.1")))
    )
  }

  override protected def afterAll(): Unit = {
    appOriginTlsv11.stop()
    appOriginTlsv12.stop()
    super.afterAll()
  }

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches(matches)
    matchingStrategy
  }

  describe("Backend Service TLS Protocol Setting") {

    it("Proxies to TLSv1.1 origin when TLSv1.1 support enabled.") {
      val response1 = decodedRequest(httpRequest("/tls11/a"))
      assert(response1.status() == OK)
      assert(response1.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsv11.verify(
        getRequestedFor(
          urlEqualTo("/tls11/a"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))

      val response2 = decodedRequest(httpRequest("/tlsDefault/a2"))
      assert(response2.status() == OK)
      assert(response2.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsDefault.verify(
        getRequestedFor(
          urlEqualTo("/tlsDefault/a2"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))
    }

    it("Proxies to TLSv1.2 origin when TLSv1.2 support is enabled.") {
      val response1 = decodedRequest(httpRequest("/tlsDefault/b1"))
      assert(response1.status() == OK)
      assert(response1.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsDefault.verify(
        getRequestedFor(urlEqualTo("/tlsDefault/b1"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))

      val response2 = decodedRequest(httpRequest("/tls12/b2"))
      assert(response2.status() == OK)
      assert(response2.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsv12.verify(
        getRequestedFor(urlEqualTo("/tls12/b2"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))
    }

    it("Refuses to connect to TLSv1.1 origin when TLSv1.1 is disabled") {
      val response = decodedRequest(httpRequest("/tls11-to-tls12/c"))

      assert(response.status() == BAD_GATEWAY)
      assert(response.bodyAs(UTF_8) == "Site temporarily unavailable.")

      appOriginTlsv12B.verify(0, getRequestedFor(urlEqualTo("/tls11-to-tls12/c")))
    }
  }

  def originResponse(appId: String) = aResponse
    .withStatus(OK.code())
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

}
