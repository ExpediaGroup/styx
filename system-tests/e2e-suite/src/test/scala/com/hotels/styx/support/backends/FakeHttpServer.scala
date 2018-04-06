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
package com.hotels.styx.support.backends

import java.nio.charset.Charset

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.server.UrlMatchingStrategies.urlStartingWith
import com.hotels.styx.support.server.{FakeHttpServer => JavaFakeHttpServer}
import com.hotels.styx.utils.StubOriginHeader.STUB_ORIGIN_INFO

import scala.collection.JavaConverters._

object FakeHttpServer {

  case class HttpsStartupConfig(httpsPort: Int = 0,
                                adminPort: Int = 0,
                                appId: String = "generic-app",
                                originId: String = "generic-app",
                                certificateFile: String = null,
                                certificateKeyFile: String = null,
                                protocols: Seq[String] = Seq("TLSv1.1", "TLSv1.2"),
                                cipherSuites: Seq[String] = Seq(),
                                sslProvider: String = "JDK"
                               ) {

    def start(): MockOriginServer = {

      var builder = new HttpsConnectorConfig.Builder()
      .sslProvider(sslProvider)
        .port(httpsPort)
        .protocols(protocols:_*)
        .cipherSuites(cipherSuites.toList.asJava)
      builder = if (certificateFile != null) builder.certificateFile(certificateFile) else builder
      builder = if (certificateKeyFile != null) builder.certificateFile(certificateKeyFile) else builder

      MockOriginServer.create(appId, originId, adminPort, builder.build()).start()
    }
  }

  case class HttpStartupConfig(port: Int = 0,
                               appId: String = "generic-app",
                               originId: String = "generic-app-01"
                              ) {
    private def asJava: WireMockConfiguration = {
      val wmConfig = if (port == 0) {
        new WireMockConfiguration().dynamicPort()
      } else {
        new WireMockConfiguration().port(port)
      }

      wmConfig.httpsPort(-1)
    }

    def start(): JavaFakeHttpServer = {
      val server = JavaFakeHttpServer.newHttpServer(appId, originId, this.asJava).start()
      println("server ports: " + server.adminPort() + " " + server.port())

      val response = s"Response From $appId:$originId, localhost:$port"

      server.stub(urlStartingWith("/"), aResponse
        .withStatus(200)
        .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
        .withHeader(STUB_ORIGIN_INFO.toString, s"{appId.toUpperCase}-$originId")
        .withBody(response))

      server
    }
  }

  private object WireMockDefaults {
    val https = new WireMockConfiguration().httpsSettings()
  }

}
