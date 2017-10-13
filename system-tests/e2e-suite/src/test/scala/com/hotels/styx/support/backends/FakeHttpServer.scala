/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.support.backends

import java.nio.charset.Charset

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.support.HostAndPorts.freePort
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import com.hotels.styx.support.server.{FakeHttpServer => JavaFakeHttpServer}

object FakeHttpServer {

  case class HttpsStartupConfig(httpsPort: Int = 0,
                                adminPort: Int = 0,
                                appId: String = "generic-app",
                                originId: String = "generic-app",
                                keyStorePath: String = WireMockDefaults.https.keyStorePath(),
                                keyStorePassword: String = WireMockDefaults.https.keyStorePassword(),
                                trustStorePath: String = WireMockDefaults.https.trustStorePath(),
                                trustStorePassword: String = WireMockDefaults.https.trustStorePassword(),
                                needClientAuth: Boolean = WireMockDefaults.https.needClientAuth()
                               ) {
    private def asJava: WireMockConfiguration = new WireMockConfiguration()
      .port(if (adminPort == 0) freePort() else adminPort)
      .httpsPort(if (httpsPort == 0) freePort() else httpsPort)
      .keystorePath(keyStorePath)
      .keystorePassword(keyStorePassword)
      .trustStorePath(trustStorePath)
      .trustStorePassword(trustStorePassword)
      .needClientAuth(needClientAuth)

    def start(): JavaFakeHttpServer = JavaFakeHttpServer.newHttpServer(appId, originId, this.asJava).start()
  }

  case class HttpStartupConfig(port: Int = 0,
                               appId: String = "generic-app",
                               originId: String = "generic-app-01"
                              ) {
    private def asJava: WireMockConfiguration = {
      new WireMockConfiguration()
        .port(if (port == 0) freePort() else port)
        .httpsPort(-1)
    }

    def start(): JavaFakeHttpServer = {
      val server = JavaFakeHttpServer.newHttpServer(appId, originId, this.asJava).start()
      println("server ports: " + server.adminPort() + " " + server.port())

      val response = s"Response From $appId:$originId, localhost:$port"

      server.stub(urlStartingWith("/"), aResponse
        .withStatus(200)
        .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
        .withHeader(STUB_ORIGIN_INFO.toString, s"{appId.toUpperCase}-$originId")
        .withBody(response))

      server
    }
  }

  private object WireMockDefaults {
    val https = new WireMockConfiguration().httpsSettings()
  }

}
