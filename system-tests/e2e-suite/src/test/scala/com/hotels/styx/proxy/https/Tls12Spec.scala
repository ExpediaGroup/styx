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
package com.hotels.styx.proxy.https

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.HttpMethod.GET
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.{FullHttpClient, FullHttpRequest}
import com.hotels.styx.client.SimpleHttpClient
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.{SSLSetup, StyxProxySpec}
import org.scalatest.{FunSpec, ShouldMatchers}

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Await
import scala.concurrent.duration._

class Tls12Spec extends FunSpec
  with StyxProxySpec
  with HttpResponseImplicits
  with ShouldMatchers
  with SSLSetup {

  val crtFile = fixturesHome(classOf[ProtocolsSpec], "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(classOf[ProtocolsSpec], "/ssl/testCredentials.key").toString
  val clientV12 = newClient(Seq("TLSv1.2"))
  val clientV11 = newClient(Seq("TLSv1.1"))

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
        |
      """.stripMargin
  )

  val recordingBackend = FakeHttpServer.HttpsStartupConfig()
    .start()

  override protected def beforeAll() = {
    super.beforeAll()
    styxServer.setBackends("/secure" -> HttpsBackend(
      "https-app", Origins(recordingBackend), TlsSettings()
    ))
  }

  override protected def afterAll() = {
    recordingBackend.stop()
    super.afterAll()
  }

  describe("TLS protocol restriction") {
    recordingBackend.stub(urlPathEqualTo("/secure"), aResponse.withStatus(200))

    it("Accepts TLS 1.2 only") {
      val req = new FullHttpRequest.Builder(GET, styxServer.secureRouterURL("/secure"))
        .header(HOST, styxServer.httpsProxyHost)
        .secure(true)
        .build()

      val resp = Await.result(clientV12.sendRequest(req).toScala, 3.seconds)

      resp.status() should be(OK)
    }

    it("Refuses TLS 1.1 when TLS 1.2 is required") {
      val req = new FullHttpRequest.Builder(GET, styxServer.secureRouterURL("/secure"))
        .header(HOST, styxServer.httpsProxyHost)
        .secure(true)
        .build()

      an[RuntimeException] should be thrownBy {
        Await.result(clientV11.sendRequest(req).toScala, 3.seconds)
      }
    }
  }

  def newClient(supportedProtocols: Seq[String]): FullHttpClient =
    new SimpleHttpClient.Builder()
      .tlsSettings(TlsSettings(protocols = supportedProtocols).asJava)
      .build()

}
