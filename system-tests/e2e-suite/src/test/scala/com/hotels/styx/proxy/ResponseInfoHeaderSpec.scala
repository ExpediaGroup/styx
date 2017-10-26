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
package com.hotels.styx.proxy

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.client.StyxHeaderConfig.STYX_INFO_DEFAULT
import com.hotels.styx.api.support.HostAndPorts._
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.support.matchers.IsOptional.{isValue, matches}
import com.hotels.styx.support.matchers.RegExMatcher.matchesRegex
import com.hotels.styx.{MockServer, StyxConfiguration, StyxProxySpec}
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus._
import org.hamcrest.MatcherAssert.assertThat
import org.scalatest.{BeforeAndAfter, FunSpec}

class VersionPresentInResponseHeaderSpec extends FunSpec
  with StyxProxySpec
  with StyxConfiguration
  with BeforeAndAfter {
  val mockServer = new MockServer(freePort())
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
      val req = new HttpRequest.Builder(GET, "/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()
      val (resp, body) = decodedRequest(req)
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
  val mockServer = new MockServer(freePort())
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
      val req = new HttpRequest.Builder(GET, "/")
        .addHeader(HOST, styxServer.proxyHost)
        .build()
      val (resp, body) = decodedRequest(req)
      backend.verify(getRequestedFor(urlPathEqualTo("/")))
      assert(resp.status() == OK)
      assertThat(resp.header(STYX_INFO_DEFAULT), matches(matchesRegex("^noJvmRouteSet;[0-9a-f-]+$")))
    }
  }
}