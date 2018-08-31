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
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.matchers.IsOptional.matches
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.{MockServer, StyxConfiguration, StyxProxySpec}
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.{BeforeAndAfter, FunSpec}

class VersionPresentInResponseHeaderSpec extends FunSpec
  with StyxProxySpec
  with StyxConfiguration
  with BeforeAndAfter {
  val mockServer = new MockServer(0)
  val backend = FakeHttpServer.HttpStartupConfig().start()
  override val styxConfig = StyxConfig(yamlText=
    """
      |styxHeaders:
      |  styxInfo:
      |    valueFormat: "{VERSION};{REQUEST_ID};{INSTANCE}"
    """.stripMargin)

  before {
    styxServer.setBackends(
      "/" -> HttpBackend("default-app", Origins(backend)))

    backend
      .stub(urlPathEqualTo("/"), aResponse
        .withStatus(200)
      )
  }
  describe("Styx Info response header configured to contain version") {
    it("should respond with version in response header") {
      val req = get("/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()
      val resp = decodedRequest(req)
      backend.verify(getRequestedFor(urlPathEqualTo("/")))
      assert(resp.status() == OK)
      assertThat(resp.header(STYX_INFO_DEFAULT), matches(matchesRegex("^STYX.[._a-zA-Z0-9-]+;[0-9a-f-]+;noJvmRouteSet$")))
    }
  }
}

class VersionAbsentFromResponseHeaderSpec extends FunSpec
  with StyxProxySpec
  with StyxConfiguration
  with BeforeAndAfter {
  val mockServer = new MockServer(0)
  val backend = FakeHttpServer.HttpStartupConfig().start()
  override val styxConfig = StyxConfig()

  before {
    styxServer.setBackends(
      "/" -> HttpBackend("default-app", Origins(backend)))

    backend
      .stub(urlPathEqualTo("/"), aResponse
        .withStatus(200)
      )
  }

  describe("Styx Info response header default format") {
    it("should respond without version in response header") {
      val req = get("/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()
      val resp = decodedRequest(req)
      backend.verify(getRequestedFor(urlPathEqualTo("/")))
      assert(resp.status() == OK)
      assertThat(resp.header(STYX_INFO_DEFAULT), matches(matchesRegex("^noJvmRouteSet;[0-9a-f-]+$")))
    }
  }
}