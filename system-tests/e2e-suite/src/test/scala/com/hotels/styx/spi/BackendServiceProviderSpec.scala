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
package com.hotels.styx.spi

import com.google.common.net.HostAndPort
import com.google.common.net.HostAndPort._
import com.hotels.styx.StyxProxySpec
import com.hotels.styx.api.FullHttpRequest
import com.hotels.styx.api.FullHttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.support.backends.FakeHttpServer
import com.hotels.styx.support.configuration.StyxConfig
import org.scalatest.FunSpec

class BackendServiceProviderSpec extends FunSpec with StyxProxySpec {
  val normalBackend = FakeHttpServer.HttpStartupConfig().start()
  val pluginsFolder = resourcesPluginsPath

  override val styxConfig = StyxConfig(
    yamlText = s"""
        |proxy:
        |  connectors:
        |    http:
        |      port: 8080
        |
        |admin:
        |  connectors:
        |    http:
        |      port: 9000
        |
        |services:
        |  factories:
        |    backendServiceRegistry:
        |      factory:
        |        class: "testgrp.TestBackendProvider$$Factory"
        |        classPath: "$pluginsFolder"
        |      config:
        |        backendService:
        |            id: "app"
        |            path: "/"
        |            connectionPool:
        |              maxConnectionsPerHost: 45
        |              maxPendingConnectionsPerHost: 15
        |              socketTimeoutMillis: 120000
        |              connectTimeoutMillis: 8000
        |              pendingConnectionTimeoutMillis: 8000
        |            origins:
        |              - id: "app-01"
        |                host: "localhost:${normalBackend.port()}"
        |                responseTimeoutMillis: 60000
        """.stripMargin('|')
  )

  override protected def beforeAll(): Unit = {
    styxServer = styxConfig.startServer()
  }

  override protected def afterAll(): Unit = {
    normalBackend.stop()
    super.afterAll()
  }

  describe("SPI Backend Service Provider") {
    it("Loads during startup") {
      decodedRequest(anHttpRequest).status() should be(OK)
    }
  }

  def styxHostAndPort: HostAndPort = {
    fromParts("localhost", styxServer.httpPort)
  }

  def anHttpRequest: FullHttpRequest = {
    get(styxServer.routerURL("/pluginPipelineSpec/")).build()
  }

}
