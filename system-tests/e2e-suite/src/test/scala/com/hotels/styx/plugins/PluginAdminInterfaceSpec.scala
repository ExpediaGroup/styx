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

import java.nio.charset.StandardCharsets.UTF_8
import java.util

import com.google.common.net.HostAndPort
import com.google.common.net.HostAndPort._
import com.google.common.net.MediaType.PLAIN_TEXT_UTF_8
import com.hotels.styx.api._
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{PluginAdapter, StyxClientSupplier, StyxProxySpec}
import org.scalatest.FunSpec

import scala.collection.JavaConverters._

class PluginAdminInterfaceSpec extends FunSpec with StyxProxySpec with StyxClientSupplier {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()

  override val styxConfig = StyxConfig(plugins = List(
    "plugx" -> new PluginX,
    "plugy" -> new PluginY,
    "plugz" -> new PluginZ,
    "plugw" -> new PluginWithNoAdminFeatures
  ))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app-1", Origins(normalBackend)))
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("styx admin server") {
    it("Exposes plugin admin interface under /plugins/<name> endpoint") {
      val respX1 = get(styxServer.adminURL("/admin/plugins/plugx/path/one"))
      val respX2 = get(styxServer.adminURL("/admin/plugins/plugx/path/two"))
      val respY1 = get(styxServer.adminURL("/admin/plugins/plugy/path/one"))
      val respY2 = get(styxServer.adminURL("/admin/plugins/plugy/path/two"))

      respX1.bodyAs(UTF_8) should be("X: Response from first admin interface")
      respX2.bodyAs(UTF_8) should be("X: Response from second admin interface")
      respY1.bodyAs(UTF_8) should be("Y: Response from first admin interface")
      respY2.bodyAs(UTF_8) should be("Y: Response from second admin interface")
    }

    it("Joins plugin admin path with a path separator character, if one is missing") {
      val respZ1 = get(styxServer.adminURL("/admin/plugins/plugz/path/one"))
      val respZ2 = get(styxServer.adminURL("/admin/plugins/plugz/path/two"))
      respZ1.bodyAs(UTF_8) should be("Z: Response from first admin interface")
      respZ2.bodyAs(UTF_8) should be("Z: Response from second admin interface")
    }

    it("Represents link to plugins index on admin server index page") {
      val response = get(styxServer.adminURL("/admin"))

      response.bodyAs(UTF_8) should include("<a href='/admin/plugins'>Plugins</a>")
    }

    it("Represents links to plugin indexes on plugins index page") {
      val response = get(styxServer.adminURL("/admin/plugins"))

      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugx'>plugx</a>")
      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugy'>plugy</a>")
      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugz'>plugz</a>")
      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugw'>plugw</a>")
    }

    it("Represents links to plugin endpoints on admin server plugin index pages") {
      val response = get(styxServer.adminURL("/admin/plugins/plugx"))

      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugx/path/one'>plugx: path/one</a>")
      response.bodyAs(UTF_8) should include("<a href='/admin/plugins/plugx/path/two'>plugx: path/two</a>")
    }

    it("Plugins without any admin features should display message instead of index") {
      val response = get(styxServer.adminURL("/admin/plugins/plugw"))

      response.bodyAs(UTF_8) should include("This plugin (plugw) does not expose any admin interfaces")
    }
  }

  private def get(url: String) = {
    decodedRequest(FullHttpRequest.get(url).build())
  }

  def styxHostAndPort: HostAndPort = {
    fromParts("localhost", styxServer.httpPort)
  }

  def anHttpRequest: HttpRequest = {
    HttpRequest.get(styxServer.routerURL("/pluginPipelineSpec/")).build()
  }

  private class PluginX extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): StyxObservable[HttpResponse] = chain.proceed(request)

    override def adminInterfaceHandlers(): util.Map[String, HttpHandler] = Map[String, HttpHandler](
      "/path/one" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "X: Response from first admin interface"),
      "/path/two" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "X: Response from second admin interface")
    ).asJava
  }

  private class PluginY extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): StyxObservable[HttpResponse] = chain.proceed(request)

    override def adminInterfaceHandlers(): util.Map[String, HttpHandler] = Map[String, HttpHandler](
      "/path/one" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Y: Response from first admin interface"),
      "/path/two" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Y: Response from second admin interface")
    ).asJava
  }

  private class PluginZ extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): StyxObservable[HttpResponse] = chain.proceed(request)

    override def adminInterfaceHandlers(): util.Map[String, HttpHandler] = Map[String, HttpHandler](
      "path/one" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Z: Response from first admin interface"),
      "path/two" -> new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "Z: Response from second admin interface")
    ).asJava
  }

  private class PluginWithNoAdminFeatures extends PluginAdapter {
    override def intercept(request: HttpRequest, chain: HttpInterceptor.Chain): StyxObservable[HttpResponse] = chain.proceed(request)

    override def adminInterfaceHandlers(): util.Map[String, HttpHandler] = Map[String, HttpHandler](

    ).asJava
  }

}
