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
package com.hotels.styx.proxy

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.scalatest.FunSpec

class OriginRestrictionSpec extends FunSpec
  with StyxProxySpec
  with DefaultStyxConfiguration {

  val backend1 = fakeOrigin("appOne", "h1")
  val backend2 = fakeOrigin("appOne", "h2")
  val backend3 = fakeOrigin("appOne", "h3")
  val backend4 = fakeOrigin("appOne", "h4")

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends("/app/" -> HttpBackend(
      "appOne", Origins(backend1, backend2, backend3, backend4)))
  }

  override protected def afterAll(): Unit = {
    backend1.stop()
    backend2.stop()
    backend3.stop()
    backend4.stop()

    super.afterAll()
  }

  describe("Routes correctly") {
    it("Routes to origin indicated by cookie.") {
      val request = get("/app/")
        .header(HOST, styxServer.proxyHost)
        .cookies(requestCookie("originRestrictionCookie", "h2"))
        .build()

      val response = decodedRequest(request)

      backend2.verify(getRequestedFor(urlEqualTo("/app/")).withHeader("Cookie", equalTo("originRestrictionCookie=h2")))
      response.header(ORIGIN_ID_DEFAULT).get() should be("h2")
    }

    it("Routes to range of origins indicated by cookie.") {
      val request = get("/app/")
        .header(HOST, styxServer.proxyHost)
        .cookies(requestCookie("originRestrictionCookie", "h(2|3)"))
        .build()

      val response = decodedRequest(request)

      response.header(ORIGIN_ID_DEFAULT).get() should (be("h2") or be("h3"))
    }

    it("If nothing matches treat as no hosts available") {
      val request = get("/app/")
        .header(HOST, styxServer.proxyHost)
        .cookies(requestCookie("originRestrictionCookie", "(?!)"))
        .build()

      val response = decodedRequest(request)

      response.status() should be(BAD_GATEWAY)
    }

    it("Routes to list of origins indicated by cookie.") {
      val request = get("/app/")
        .header(HOST, styxServer.proxyHost)
        .cookies(requestCookie("originRestrictionCookie", "h2,h[3-4]"))
        .build()

      val response = decodedRequest(request)

      response.header(ORIGIN_ID_DEFAULT).get() should (be("h2") or be("h3") or be("h4"))
    }
  }

  def fakeOrigin(appId: String, originId: String) = FakeHttpServer.HttpStartupConfig(appId = appId, originId = originId)
    .start()
    .stub(urlMatching("/.*"), aResponse.withStatus(200))

}
