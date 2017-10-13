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
package com.hotels.styx.client

import java.nio.charset.Charset

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.google.common.net.HostAndPort._
import com.hotels.styx.api.HttpHeaderNames
import com.hotels.styx.api.client.Origin
import Origin._
import com.hotels.styx.api.support.HostAndPorts
import com.hotels.styx.api.support.HostAndPorts._
import HttpHeaderNames._
import com.hotels.styx.support.server.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies._

trait OriginSupport {
  def configureAndStart(origin: Origin): FakeHttpServer = {
    val webServer: FakeHttpServer = new FakeHttpServer(origin.host().getPort).start()

    webServer.stub(urlMatching("/version.txt"), aResponse
      .withStatus(200)
      .withHeader("Stub-Origin-Info", origin.applicationInfo()))
  }

  def originAndWireMockServer(applicationId: String, originId: String): (Origin, WireMockServer) = {
    val server = new WireMockServer(wireMockConfig.port(freePort()))
    server.start()

    val origin = newOriginBuilder("localhost", server.port()).applicationId(applicationId).id(originId).build()
    origin -> server
  }

  def originAndServer(appId: String, originId: String) = {
    val server = new FakeHttpServer(HostAndPorts.freePort()).start()
    val origin = newOriginBuilder(fromParts("localhost", server.port())).applicationId(appId).id(originId).build()

    val response = "Response From localhost"
    server.stub(urlStartingWith("/"), aResponse
      .withStatus(200)
      .withHeader(CONTENT_LENGTH.toString, response.getBytes(Charset.defaultCharset()).size.toString)
      .withHeader("Stub-Origin-Info", origin.applicationInfo())
      .withBody(response))

    origin -> server
  }

}
