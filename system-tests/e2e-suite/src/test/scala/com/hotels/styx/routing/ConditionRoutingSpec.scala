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
package com.hotels.styx.routing

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ValueMatchingStrategy, WireMock}
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.api.extension
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{BackendServicesRegistrySupplier, StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, SequentialNestedSuiteExecution}

import scala.concurrent.duration._

class ConditionRoutingSpec extends FunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution
  with BackendServicesRegistrySupplier {

  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")
  val httpBackendRegistry = new MemoryBackedRegistry[extension.service.BackendService]()
  val httpsBackendRegistry = new MemoryBackedRegistry[extension.service.BackendService]()
  val crtFile = fixturesHome(this.getClass, "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(this.getClass, "/ssl/testCredentials.key").toString

  val httpServer = FakeHttpServer.HttpStartupConfig(
    appId = "app",
    originId = "app-01")
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("http-app"))

  val httpsServer = FakeHttpServer.HttpsStartupConfig(
    appId = "app-ssl",
    originId = "app-ssl-01")
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("httpsOriginWithoutCert"))

  override val styxConfig = StyxConfig(
    ProxyConfig(
      Connectors(
        HttpConnectorConfig(),
        HttpsConnectorConfig(
          cipherSuites = Seq("TLS_RSA_WITH_AES_128_GCM_SHA256"),
          certificateFile = crtFile,
          certificateKeyFile = keyFile))
    ),
    logbackXmlLocation = logback,
    additionalServices = Map(
      "http-backends" -> new RegistryServiceAdapter(httpBackendRegistry),
      "https-backends" -> new RegistryServiceAdapter(httpsBackendRegistry)
    ),
    yamlText =
      """
        |httpPipeline:
        |  name: "Main Pipeline"
        |  type: InterceptorPipeline
        |  config:
        |    handler:
        |      name: protocol-router
        |      type: ConditionRouter
        |      config:
        |        routes:
        |          - condition: protocol() == "https"
        |            destination:
        |              name: proxy-to-https
        |              type: BackendServiceProxy
        |              config:
        |                backendProvider: "https-backends"
        |        fallback:
        |          name: proxy-to-http
        |          type: BackendServiceProxy
        |          config:
        |            backendProvider: "http-backends"
      """.stripMargin
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    setBackends(httpBackendRegistry, "/" -> HttpBackend(
      "appOne-http",
      Origins(httpServer)
    ))

    setBackends(httpsBackendRegistry, "/" -> HttpsBackend(
      "appOne-https",
      Origins(httpsServer),
      TlsSettings(),
      responseTimeout = 3.seconds
    ))
  }

  println("httpOrigin: " + httpServer.port())
  println("httpsOriginWithoutCert: " + httpsServer.port())

  override protected def afterAll(): Unit = {
    httpsServer.stop()
    httpServer.stop()
    super.afterAll()
  }

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  def httpsRequest(path: String) = get(styxServer.secureRouterURL(path)).build()

  def valueMatchingStrategy(matches: String) = {
    val matchingStrategy = new ValueMatchingStrategy()
    matchingStrategy.setMatches(matches)
    matchingStrategy
  }

  describe("Styx routing of HTTP requests") {
    it("Routes HTTP protocol to HTTP origins") {
      val response = decodedRequest(httpRequest("/app.1"))

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      httpServer.verify(
        getRequestedFor(urlEqualTo("/app.1"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("http")))
    }

    it("Routes HTTPS protocol to HTTPS origins") {
      val response = decodedRequest(httpsRequest("/app.2"))

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      httpsServer.verify(
        getRequestedFor(urlEqualTo("/app.2"))
          .withHeader("X-Forwarded-Proto", valueMatchingStrategy("https")))
    }
  }

  def originResponse(appId: String) = aResponse
    .withStatus(OK.code())
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

}
