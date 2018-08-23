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
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpResponseStatus._
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.matchers.IsOptional.matches
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.{MockServer, StyxConfiguration, StyxProxySpec}
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.is
import org.scalatest.{BeforeAndAfter, FunSpec}

/**
  *
  */
class HeaderNamesSpec  extends FunSpec
  with StyxProxySpec
  with StyxConfiguration
  with BeforeAndAfter {
  val mockServer = new MockServer(0)
  val backend = FakeHttpServer.HttpStartupConfig().start()
  override val styxConfig = StyxConfig(yamlText=
    """
      |styxHeaders:
      |  styxInfo:
      |    name: "styx-info-foo"
      |  originId:
      |    name: "origin-id-bar"
      |  requestId:
      |    name: "request-id-baz"
      |
    """.stripMargin)

  before {
    styxServer.setBackends(
      "/" -> HttpBackend("default-app", Origins(backend)))

    backend
      .stub(urlPathEqualTo("/"), aResponse
        .withStatus(200)
      )
  }
  describe("Proxied responses have expected headers added") {
    it("should add response headers") {
      val req = get("/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()
      val resp = decodedRequest(req)
      backend.verify(getRequestedFor(urlPathEqualTo("/")))
      assert(resp.status() == OK)
      assertThat(resp.header("styx-info-foo"), matches(matchesRegex("^noJvmRouteSet;[0-9a-f-]+$")))
      assertThat(resp.header("origin-id-bar"), matches(is("generic-app-01")))

      verify(getRequestedFor(urlPathEqualTo("/"))
        .withHeader("request-id-baz", matching("[0-9a-f-]+")))
    }
  }
}
