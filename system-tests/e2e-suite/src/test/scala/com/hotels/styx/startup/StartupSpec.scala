/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup

import com.github.tomakehurst.wiremock.client.WireMock._
import com.hotels.styx.api.HttpHeaderNames._
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.RequestCookie.requestCookie
import com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{DefaultStyxConfiguration, StyxProxySpec}
import org.scalatest.FunSpec

class StartupSpec extends FunSpec
  with StyxProxySpec
//  with DefaultStyxConfiguration
{

  val backend1 = fakeOrigin("appOne", "h1")

  override val styxConfig = StyxConfig(
    yamlText = s"""
                  |plugins:
                  |  active: PluginA
                  |  all:
                  |     PluginA:
                  |       factory:
                  |          class: com.hotels.styx.startup.SlowToStartPlugin$$Factory
                  |          classPath: " "
                  |       config: /my/plugin/config/directory
        """.stripMargin('|')
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    styxServer.setBackends("/app/" -> HttpBackend(
      "appOne", Origins(backend1)))
  }

  override protected def afterAll(): Unit = {
    backend1.stop()

    super.afterAll()
  }

  describe("Admin and proxy servers are started independently") {
    ignore("Exposes admin endpoint while proxy server is still starting") {

      println("TEST BEGINS")

      val request = get("http://" + styxServer.adminHost + "/ping")
        .header(HOST, styxServer.adminHost)
        .build()

      val response = decodedRequest(request)

      println(response.status())

//      backend2.verify(getRequestedFor(urlEqualTo("/app/")).withHeader("Cookie", equalTo("originRestrictionCookie=h2")))
//      response.header(ORIGIN_ID_DEFAULT).get() should be("h2")
    }
  }

  def fakeOrigin(appId: String, originId: String) = FakeHttpServer.HttpStartupConfig(appId = appId, originId = originId)
    .start()
    .stub(urlMatching("/.*"), aResponse.withStatus(200))

}
