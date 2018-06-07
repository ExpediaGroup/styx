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

import com.google.common.net.HostAndPort
import com.google.common.net.HostAndPort._
import com.hotels.styx.api.FullHttpRequest
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.{HttpBackend, Origins, StyxConfig}
import com.hotels.styx.{ExamplePluginJarLocation, StyxProxySpec}
import org.scalatest.FunSpec

class PluginPipelineSpec extends FunSpec with StyxProxySpec {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()
  val pluginsFolder = ExamplePluginJarLocation.createTemporarySharedDirectoryForJars()

  override val styxConfig = StyxConfig(
    yamlText = s"""
        |plugins:
        |  active: PluginA
        |  all:
        |     PluginA:
        |       factory:
        |          class: testgrp.TestPluginModule
        |          classPath: "$pluginsFolder"
        |       config: /my/plugin/config/directory
        """.stripMargin('|')
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    styxServer.setBackends("/" -> HttpBackend("app1", Origins(normalBackend)))
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("plugins ") {

    it("Styx loads the plugins during startup.") {
      val response = decodedRequest(anHttpRequest)

      response.header("X-Hcom-Plugins").get() should be("test-plugin-a")
      response.header("X-Hcom-Plugins-List").get() should be("PluginA")
    }

    it("Styx exposes the plugin configuration directory") {
      val response = decodedRequest(anHttpRequest)
      response.header("X-Hcom-Plugin-Configuration-Path").get() should be("/my/plugin/config/directory")
      response.header("X-Hcom-Plugins-List").get() should be("PluginA")
    }
  }

  def styxHostAndPort: HostAndPort = {
    fromParts("localhost", styxServer.httpPort)
  }

  def anHttpRequest: FullHttpRequest = {
    FullHttpRequest.get(styxServer.routerURL("/pluginPipelineSpec/")).build()
  }

}
