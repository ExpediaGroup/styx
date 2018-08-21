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
import com.hotels.styx.api.FullHttpRequest
import com.hotels.styx.api.HttpHeaderNames.{X_FORWARDED_PROTO, _}
import com.hotels.styx.api.HttpMethod.GET
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.infrastructure.HttpResponseImplicits
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration._
import com.hotels.styx.{SSLSetup, StyxClientSupplier, StyxProxySpec}
import org.scalatest.{FunSpec, ShouldMatchers}

class HttpsSpec extends FunSpec
  with StyxProxySpec
  with HttpResponseImplicits
  with StyxClientSupplier
  with ShouldMatchers
  with SSLSetup {

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
    )
  )

  val recordingBackend = FakeHttpServer.HttpsStartupConfig().start()

  override protected def afterAll() = {
    recordingBackend.stop()
    super.afterAll()
  }

  describe("Terminating https") {
    recordingBackend.stub(urlPathEqualTo("/secure"), aResponse.withStatus(200))

    it("should set the X-Forward-Proto header for https request") {
      styxServer.setBackends("/secure" -> HttpsBackend(
        "https-app", Origins(recordingBackend), TlsSettings()
      ))

      val req = new FullHttpRequest.Builder(GET, styxServer.secureRouterURL("/secure"))
        .header(HOST, styxServer.httpsProxyHost)
        .build()

      req.isSecure should be(true)

      val resp = decodedRequest(req)
      resp.status() should be(OK)

      recordingBackend.verify(getRequestedFor(urlPathEqualTo("/secure"))
        .withHeader(X_FORWARDED_PROTO.toString, equalTo("https"))
        .withHeader("Host", equalTo(styxServer.httpsProxyHost))
      )
    }
  }

}
