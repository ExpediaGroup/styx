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
package com.hotels.styx.proxy.https

import ch.qos.logback.classic.Level.INFO
import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.client.{HttpClient, StyxHttpClient}
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent
import com.hotels.styx.support.matchers.LoggingTestSupport
import com.hotels.styx.{SSLSetup, StyxProxySpec}
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Await
import scala.concurrent.duration._

class TlsErrorSpec extends FunSpec
  with StyxProxySpec
  with HttpResponseImplicits
  with Matchers
  with SSLSetup
  with Eventually {

  val crtFile = fixturesHome(classOf[ProtocolsSpec], "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(classOf[ProtocolsSpec], "/ssl/testCredentials.key").toString
  val clientV11 = newClient(Seq("TLSv1.1"))
  var log: LoggingTestSupport = _

  override val styxConfig = StyxYamlConfig(
    yamlConfig =
      s"""
         |proxy:
         |  connectors:
         |    https:
         |      port: 0
         |      sslProvider: JDK
         |      certificateFile: $crtFile
         |      certificateKeyFile: $keyFile
         |      protocols:
         |       - TLSv1.2
         |admin:
         |  connectors:
         |    http:
         |      port: 0
         |services:
         |  factories: {}
      """.stripMargin
  )

  val app = FakeHttpServer.HttpsStartupConfig().start()

  override protected def beforeAll() = {
    super.beforeAll()
    log = new LoggingTestSupport(classOf[HttpPipelineHandler])
    styxServer.setBackends("/secure" -> HttpsBackend(
      "https-app", Origins(app), TlsSettings()
    ))
  }

  override protected def afterAll() = {
    app.stop()
    super.afterAll()
    log.stop()
  }

  describe("TLS errors handling") {
    app.stub(urlPathEqualTo("/secure"), aResponse.withStatus(200))

    it("Logs an SSL handshake exception") {
      val serverPort = styxServer.proxyHttpsAddress().getPort

      val req = HttpRequest.get(styxServer.secureRouterURL("/secure"))
        .header(HOST, styxServer.httpsProxyHost)
        .build()

      an[RuntimeException] should be thrownBy {
        Await.result(clientV11.sendRequest(req).toScala, 3.seconds)
      }

      val message =
        """SSL handshake failure from incoming connection cause=".*TLSv1.1.*"""

      eventually(timeout(3 seconds)) {
        assertThat(log.log(), hasItem(loggingEvent(INFO, message)))
      }
    }
  }

  def newClient(supportedProtocols: Seq[String]): HttpClient =
    new StyxHttpClient.Builder()
      .tlsSettings(TlsSettings(protocols = supportedProtocols).asJava)
      .build()

}
