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
package com.hotels.styx.plugins

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx._
import com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING
import com.hotels.styx.api.HttpHeaderValues.CHUNKED
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.server.UrlMatchingStrategies._
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.is
import org.scalatest.FunSpec

// TODO: Mikko no longer necessary?

class ResubscribingPluginSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration {

  val mockServer = FakeHttpServer.HttpStartupConfig().start()

//  styxPlugins = List(
//    namedPlugin("resubscribingPlugin", new ResubscribingPlugin()))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
//    resetBackendRoutes()
    mockServer.start()

//    addBackendRoute("/foobar", new Builder()
//      .origins(newOriginFromBackend(mockServer))
//      .responseTimeoutMillis(5000)
//      .build())
  }

  override protected def afterAll(): Unit = {
    mockServer.stop()
    super.afterAll()
  }

  ignore("Styx as a plugin container") {

    it("Returns connections back to pool when plugin performs tasks on separate thread pool") {
      mockServer.stub(urlStartingWith("/foobar"), aResponse
        .withStatus(200)
        .withHeader(TRANSFER_ENCODING.toString, CHUNKED.toString)
      )

      val request = get(styxServer.routerURL("/foobar"))
        .addHeader("Content-Length", "0")
        .build()

      val response = decodedRequest(request)

      assertThat(response.status(), is(INTERNAL_SERVER_ERROR))

      mockServer.verify(1, getRequestedFor(urlStartingWith("/foobar")))
    }
  }
}
