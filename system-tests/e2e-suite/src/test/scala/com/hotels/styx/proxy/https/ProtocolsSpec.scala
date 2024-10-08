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
package com.hotels.styx.proxy.https

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames.X_FORWARDED_PROTO
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.{StyxClientSupplier, StyxProxySpec}
import io.netty.buffer.{ByteBuf, Unpooled}
import org.scalatest.SequentialNestedSuiteExecution
import org.scalatest.funspec.AnyFunSpec
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration._

class ProtocolsSpec extends AnyFunSpec
  with StyxProxySpec
  with StyxClientSupplier
  with SequentialNestedSuiteExecution {

  private val LOGGER = LoggerFactory.getLogger(classOf[ProtocolsSpec])
  val logback = fixturesHome(this.getClass, "/conf/logback/logback-debug-stdout.xml")
  val crtFile = fixturesHome(this.getClass, "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(this.getClass, "/ssl/testCredentials.key").toString
  val keystore = fixturesHome(this.getClass, "/ssl/protocolsSpec/keystore").toString
  val truststore = fixturesHome(this.getClass, "/ssl/protocolsSpec/truststore").toString

  val httpServer = FakeHttpServer.HttpStartupConfig(
    appId = "app",
    originId = "app-01")
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("http-app"))

  val httpsOriginWithoutCert = FakeHttpServer.HttpsStartupConfig(
    appId = "app-ssl",
    originId = "app-ssl-01")
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("httpsOriginWithoutCert"))

  val httpsOriginWithCert = FakeHttpServer.HttpsStartupConfig(
    appId = "app-ssl",
    originId = "app-ssl-02",
    certificateFile =  crtFile,
    certificateKeyFile = keyFile
  )
    .start()
    .stub(WireMock.get(urlMatching("/.*")), originResponse("httpsOriginWithCert"))

  override val styxConfig = StyxConfig(
    ProxyConfig(
      Connectors(
        HttpConnectorConfig(),
        HttpsConnectorConfig(
          cipherSuites = Seq("TLS_RSA_WITH_AES_128_GCM_SHA256"),
          certificateFile = crtFile,
          certificateKeyFile = keyFile))
    ),
    logbackXmlLocation = logback
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends(
      "/http/" -> HttpBackend(
        "appOne-http",
        Origins(httpServer)
      ),
      "/https/trustAllCerts/" -> HttpsBackend(
        "appOne-https",
        Origins(httpsOriginWithoutCert),
        TlsSettings(),
        responseTimeout = 3.seconds
      ),
      "/https/authenticate/secure/" -> HttpsBackend(
        "app2-https",
        Origins(httpsOriginWithCert),
        TlsSettings(
          trustStorePath = truststore,
          trustStorePassword = "123456"
        ),
        responseTimeout = 3.seconds
      ),
      "/https/authenticate/insecure/" -> HttpsBackend(
        "app3-https",
        Origins(httpsOriginWithoutCert),
        TlsSettings(
          authenticate = true,
          trustStorePath = truststore,
          trustStorePassword = "123456"
        ),
        responseTimeout = 3.seconds
      )
    )

  }

  LOGGER.info("httpOrigin: " + httpServer.port())
  LOGGER.info("httpsOriginWithCert: " + httpsOriginWithCert.port())
  LOGGER.info("httpsOriginWithoutCert: " + httpsOriginWithoutCert.port())

  override protected def afterAll(): Unit = {
    httpsOriginWithoutCert.stop()
    httpsOriginWithCert.stop()
    httpServer.stop()
    super.afterAll()
  }

  def httpRequest(path: String) = get(styxServer.routerURL(path)).build()

  def httpsRequest(path: String) = get(styxServer.secureRouterURL(path)).build()

  describe("Styx routing of HTTP requests") {
    it("Proxies HTTP protocol to HTTP origins") {
      val response = decodedRequest(httpRequest("/http/app.x.1"))

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      httpServer.verify(
        getRequestedFor(urlEqualTo("/http/app.x.1"))
          .withHeader("X-Forwarded-Proto", matching("http")))
    }

    it("Proxies HTTP protocol to HTTPS origins") {
      val response = decodedRequest(httpRequest("/https/trustAllCerts/foo.2"))

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      httpsOriginWithoutCert.verify(
        getRequestedFor(urlEqualTo("/https/trustAllCerts/foo.2"))
          .withHeader("X-Forwarded-Proto", matching("http")))
    }

    it("Retains existing X-Forwarded-Proto header unmodified") {
      val response = decodedRequest(
        httpRequest("/http/app.x.3")
          .newBuilder().header(X_FORWARDED_PROTO, "https")
          .build
      )

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")

      httpServer.verify(
        getRequestedFor(urlEqualTo("/http/app.x.3"))
          .withHeader("X-Forwarded-Proto", matching("https")))
    }
  }

  describe("Styx routing of HTTPS requests") {

    it("Proxies HTTPS requests to HTTP backend") {
      val response = decodedRequest(httpsRequest("/http/app.x.4"), secure = true)

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")
      httpServer.verify(
        getRequestedFor(urlEqualTo("/http/app.x.4"))
          .withHeader("X-Forwarded-Proto", matching("https"))
      )
    }

    it("Proxies HTTPS requests to HTTPS backend") {
      val response = decodedRequest(httpsRequest("/https/trustAllCerts/foo.5"), secure = true)

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")
      httpsOriginWithoutCert.verify(
        getRequestedFor(urlEqualTo("/https/trustAllCerts/foo.5"))
          .withHeader("X-Forwarded-Proto", matching("https"))
      )
    }

    it("Retains existing X-Forwarded-Proto header unmodified") {
      val response = decodedRequest(
        httpsRequest("/https/trustAllCerts/foo.6")
          .newBuilder().header(X_FORWARDED_PROTO, "http")
          .build,
        secure = true
      )

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")
      httpsOriginWithoutCert.verify(
        getRequestedFor(urlEqualTo("/https/trustAllCerts/foo.6"))
          .withHeader("X-Forwarded-Proto", matching("http"))
      )
    }
  }

  describe("Stryx origin authentication") {

    it("Authenticates origin server") {
      val request = get(styxServer.secureRouterURL("/https/authenticate/secure/foo.7"))
        .build()

      val response = decodedRequest(request, secure = true)

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")
      httpsOriginWithCert.verify(getRequestedFor(urlEqualTo("/https/authenticate/secure/foo.7")))
    }

    it("It refuses to connect to origin server with invalid credentials") {
      val request = get(styxServer.secureRouterURL("/https/authenticate/insecure/foo.8"))
        .build()

      val response = decodedRequest(request, secure = true)
      assert(response.status() == BAD_GATEWAY)

      httpsOriginWithCert.verify(0, getRequestedFor(urlEqualTo("/https/authenticate/insecure/foo.8")))
    }

    it("It doesn't attept to authenticate an origin when trustAllCerts is true") {
      val request = get(styxServer.secureRouterURL("/https/trustAllCerts/foo.9"))
        .build()

      val response = decodedRequest(request, secure = true)

      assert(response.status() == OK)
      assert(response.bodyAs(UTF_8) == "Hello, World!")
      httpsOriginWithoutCert.verify(getRequestedFor(urlEqualTo("/https/trustAllCerts/foo.9")))
    }

    def buf(string: String): ByteBuf = Unpooled.copiedBuffer(string, UTF_8)
  }

  def originResponse(appId: String) = aResponse
    .withStatus(OK.code())
    .withHeader(STUB_ORIGIN_INFO.toString, appId)
    .withBody("Hello, World!")

}
