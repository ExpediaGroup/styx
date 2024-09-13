/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import org.scalatest.SequentialNestedSuiteExecution
import org.scalatest.funspec.AnyFunSpec

import java.nio.charset.StandardCharsets.UTF_8

class TlsVersionSpec extends AnyFunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution {

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")

  val appOriginTlsv13 = FakeHttpServer.HttpsStartupConfig(
    appId = "appTls13",
    originId = "appTls13-01",
    protocols = Seq("TLSv1.3")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.3"))

  val appOriginTlsv12B = FakeHttpServer.HttpsStartupConfig(
    appId = "appTls12B",
    originId = "appTls12B-01",
    protocols = Seq("TLSv1.2")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.2 B"))

  val appOriginTlsDefault = FakeHttpServer.HttpsStartupConfig(
    appId = "appTlsDefault",
    originId = "appTlsDefault-01",
    protocols = Seq("TLSv1.2", "TLSv1.3")
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("App TLS v1.3"))

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
      |services:
      |  factories: {}
    """.stripMargin,
    logbackXmlLocation = logback)

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/tls13/" -> HttpsBackend(
        "appTls13",
        Origins(appOriginTlsv13),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.3"))),

      "/tlsDefault/" -> HttpsBackend(
        "appTlsDefault",
        Origins(appOriginTlsDefault),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.2", "TLSv1.3"))),

      "/tls12" -> HttpsBackend(
        "appTls12",
        Origins(appOriginTlsv12),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.2"))),

      "/tls12-to-tls13" -> HttpsBackend(
        "appTls13B",
        Origins(appOriginTlsv12B),
        TlsSettings(authenticate = false, sslProvider = "JDK", protocols = List("TLSv1.3")))
    )
  }

  override protected def afterAll(): Unit = {
    appOriginTlsv13.stop()
    appOriginTlsv12.stop()
    super.afterAll()
  }

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  describe("Backend Service TLS Protocol Setting") {

    it("Proxies to TLSv1.3 origin when TLSv1.3 support enabled.") {
      val response1 = decodedRequest(httpRequest("/tls13/a"))
      assert(response1.status() == OK)
      assert(response1.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsv13.verify(
        getRequestedFor(
          urlEqualTo("/tls13/a"))
          .withHeader("X-Forwarded-Proto", matching("http")))

      val response2 = decodedRequest(httpRequest("/tlsDefault/a2"))
      assert(response2.status() == OK)
      assert(response2.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsDefault.verify(
        getRequestedFor(
          urlEqualTo("/tlsDefault/a2"))
          .withHeader("X-Forwarded-Proto", matching("http")))
    }

    it("Proxies to TLSv1.2 origin when TLSv1.2 support is enabled.") {
      val response1 = decodedRequest(httpRequest("/tlsDefault/b1"))
      assert(response1.status() == OK)
      assert(response1.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsDefault.verify(
        getRequestedFor(urlEqualTo("/tlsDefault/b1"))
          .withHeader("X-Forwarded-Proto", matching("http")))

      val response2 = decodedRequest(httpRequest("/tls12/b2"))
      assert(response2.status() == OK)
      assert(response2.bodyAs(UTF_8) == "Hello, World!")

      appOriginTlsv12.verify(
        getRequestedFor(urlEqualTo("/tls12/b2"))
          .withHeader("X-Forwarded-Proto", matching("http")))
    }

    it("Refuses to connect to TLSv1.3 origin when TLSv1.3 is disabled") {
      val response = decodedRequest(httpRequest("/tls12-to-tls13/c"))

      assert(response.status() == BAD_GATEWAY)
      assert(response.bodyAs(UTF_8) == "Site temporarily unavailable.")

      appOriginTlsv12B.verify(0, getRequestedFor(urlEqualTo("/tls12-to-tls13/c")))
    }
  }

  def originResponse(appId: String) = aResponse
    .withStatus(OK.code())
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

}
