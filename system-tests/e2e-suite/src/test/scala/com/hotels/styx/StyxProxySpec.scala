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
package com.hotels.styx

import java.nio.file.Paths

import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.infrastructure.{MemoryBackedRegistry, RegistryServiceAdapter}
import com.hotels.styx.plugins.PluginPipelineSpec
import com.hotels.styx.support.configuration.{ImplicitOriginConversions, StyxBackend, StyxBaseConfig}
import com.hotels.styx.support.{ImplicitStyxConversions, configuration}
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.scalatest._
import org.slf4j.LoggerFactory


trait StyxProxySpec extends StyxClientSupplier
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Matchers
  with ImplicitOriginConversions
  with ImplicitStyxConversions
  with StyxConfiguration
  with SSLSetup {
  this: Suite =>

  private val LOGGER = LoggerFactory.getLogger(getClass)
  var backendsRegistry = new MemoryBackedRegistry[BackendService]
  var styxServer: StyxServer = _
  var meterRegistry: MeterRegistry = _

  implicit class StyxServerOperations(val styxServer: StyxServer) extends StyxServerSupplements
    with BackendServicesRegistrySupplier {
    def setBackends(backends: (String, StyxBackend)*): Unit = setBackends(backendsRegistry, backends:_*)
  }

  def resourcesPluginsPath: String = {
    val url = classOf[PluginPipelineSpec].getClassLoader.getResource("plugins")
    Paths.get(url.toURI).toString.replace("\\", "/")
  }

  override protected def beforeAll() = {
    meterRegistry = new SimpleMeterRegistry()
    styxServer = styxConfig.startServer(new RegistryServiceAdapter(backendsRegistry), meterRegistry)
    LOGGER.info("Styx http port is: [%d]".format(styxServer.httpPort))
    LOGGER.info("Styx https port is: [%d]".format(styxServer.secureHttpPort))
    super.beforeAll()
  }

  override protected def afterAll() = {
    LOGGER.info("Styx http port was: [%d]".format(styxServer.httpPort))
    LOGGER.info("Styx https port was: [%d]".format(styxServer.secureHttpPort))
    styxServer.stopAsync().awaitTerminated()
    super.afterAll()
  }
}

trait StyxConfiguration {
  def styxConfig: StyxBaseConfig
}

trait DefaultStyxConfiguration extends StyxConfiguration {
  lazy val styxConfig: StyxBaseConfig = configuration.StyxConfig()
}
