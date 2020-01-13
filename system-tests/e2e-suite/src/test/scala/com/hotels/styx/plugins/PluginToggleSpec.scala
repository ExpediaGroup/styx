/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import java.nio.charset.StandardCharsets.UTF_8

import com.hotels.styx.api.HttpRequest.{get, put}
import com.hotels.styx.api.{HttpResponse, _}
import HttpResponseStatus.OK
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{PluginAdapter, StyxClientSupplier, StyxProxySpec}
import org.scalatest.FunSpec

class PluginToggleSpec extends FunSpec with StyxProxySpec with StyxClientSupplier {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(plugins = Map("pluginUnderTest" -> new PluginUnderTest))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app-1", Origins(normalBackend)))
  }

  override protected def beforeEach(): Unit = {
    setPluginEnabled("true")

    val resp1 = decodedRequest(get(styxServer.adminURL("/admin/plugins")).build())
    resp1.status() should be(OK)
    resp1.bodyAs(UTF_8) should include("<h3>Loaded</h3><a href='/admin/plugins/pluginUnderTest'>pluginUnderTest</a><br />")

    val resp2 = decodedRequest(get(styxServer.routerURL("/")).build())
    resp2.status() should be(OK)
    resp2.bodyAs(UTF_8) should include("response-from-plugin")

    checkPluginEnabled().trim should be("enabled")
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("styx admin server") {
    it("Should move the plugin to disabled when toggled") {
      disablePlugin()

      val resp = decodedRequest(get(styxServer.adminURL("/admin/plugins")).build())

      resp.status() should be(OK)
      resp.bodyAs(UTF_8) should include("<h3>Enabled</h3><h3>Disabled</h3><a href='/admin/plugins/pluginUnderTest'>pluginUnderTest</a><br />")
    }

    it("Plugin should not be called when disabled") {
      disablePlugin()

      val resp = decodedRequest(get(styxServer.routerURL("/")).build())

      resp.status() should be(OK)
    }

    it("Plugin can be re-enabled") {
      disablePlugin()

      val outcome = setPluginEnabled("true")
      outcome.trim should be("{\"message\":\"State of 'pluginUnderTest' changed to 'enabled'\",\"plugin\":{\"name\":\"pluginUnderTest\",\"state\":\"enabled\"}}")
      checkPluginEnabled().trim should be("enabled")
    }
  }

  private def disablePlugin() = {
    val outcome = setPluginEnabled("false")

    outcome.trim should be("{\"message\":\"State of 'pluginUnderTest' changed to 'disabled'\",\"plugin\":{\"name\":\"pluginUnderTest\",\"state\":\"disabled\"}}")

    checkPluginEnabled().trim should be("disabled")
  }

  private def setPluginEnabled(enabled: String): String = {
    val resp = decodedRequest(
      put(styxServer.adminURL("/admin/tasks/plugin/pluginUnderTest/enabled"))
        .body(enabled, UTF_8)
        .build())

    resp.status() should be(OK)
    resp.bodyAs(UTF_8)
  }

  private def checkPluginEnabled(): String = {
    val resp = decodedRequest(
      get(styxServer.adminURL("/admin/tasks/plugin/pluginUnderTest/enabled"))
        .build())

    resp.status() should be(OK)
    resp.bodyAs(UTF_8)
  }

  private class PluginUnderTest extends PluginAdapter {
    override def intercept(request: LiveHttpRequest, chain: HttpInterceptor.Chain): Eventual[LiveHttpResponse] =
      Eventual.of(HttpResponse.response().body("response-from-plugin", UTF_8).build().stream)
  }

}

