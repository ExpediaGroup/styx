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
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.{FullHttpClient, FullHttpRequest}
import com.hotels.styx.client.SimpleHttpClient
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.{SSLSetup, StyxProxySpec}
import io.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{FunSpec, ShouldMatchers}

import scala.concurrent.Await

class EmptyTlsProtocolsListSpec extends FunSpec
  with StyxProxySpec
  with HttpResponseImplicits
  with ShouldMatchers
  with SSLSetup {

  val crtFile = fixturesHome(this.getClass, "/ssl/testCredentials.crt").toString
  val keyFile = fixturesHome(this.getClass, "/ssl/testCredentials.key").toString
  val clientV12 : FullHttpClient = newClient(Seq("TLSv1.2"))
  val clientV11 : FullHttpClient = newClient(Seq("TLSv1.1"))

  override val styxConfig = StyxConfig(
    ProxyConfig(
      Connectors(
        HttpConnectorConfig(),
        HttpsConnectorConfig(
          certificateFile = crtFile,
          certificateKeyFile = keyFile,
          protocols = List()
        ))
    )
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

  import scala.concurrent.duration._
  import scala.compat.java8.FutureConverters.CompletionStageOps

  describe("Empty TLS protocols list") {
    recordingBackend.stub(urlPathEqualTo("/secure"), aResponse.withStatus(OK.code()))

    it("Empty TLS protocols list activates all supported protocols") {
      val req = FullHttpRequest.get(styxServer.secureRouterURL("/secure"))
        .header(HOST, styxServer.httpsProxyHost)
        .secure(true)
        .build()

      val resp1 = Await.result(clientV11.sendRequest(req).toScala, 3.seconds)
      resp1.status() should be(OK)

      val resp2 = Await.result(clientV12.sendRequest(req).toScala, 3.seconds)
      resp2.status() should be(OK)
    }
  }

  def newClient(supportedProtocols: Seq[String]): FullHttpClient = {
    new SimpleHttpClient.Builder()
      .tlsSettings(TlsSettings(protocols = supportedProtocols).asJava)
      .build()
  }

}
