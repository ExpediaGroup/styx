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
package com.hotels.styx.support.configuration

import java.nio.file.Path

import com.google.common.util.concurrent.Service
import com.hotels.styx.StyxServerSupport._
import com.hotels.styx.api.support.HostAndPorts._
import com.hotels.styx.infrastructure.Registry
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.{StyxServer, StyxServerBuilder}

import scala.collection.JavaConverters._
import scala.collection.Map

case class Connectors(httpConnectorConfig: HttpConnectorConfig,
                      httpsConnectorConfig: HttpsConnectorConfig) {
  def asJava: com.hotels.styx.server.netty.NettyServerConfig.Connectors = {
    val httpAsJava = Option(httpConnectorConfig).map(_.asJava).orNull
    val httpsAsJava = Option(httpsConnectorConfig).map(_.asJava).orNull

    new com.hotels.styx.server.netty.NettyServerConfig.Connectors(httpAsJava, httpsAsJava)
  }
}

sealed trait StyxBaseConfig {
  def logbackXmlLocation: Path

  def startServer(backendsRegistry: Registry[com.hotels.styx.client.applications.BackendService]): StyxServer
}

case class StyxConfig(proxyConfig: ProxyConfig = ProxyConfig(),
                      plugins: List[NamedPlugin] = List(),
                      logbackXmlLocation: Path = StyxBaseConfig.defaultLogbackXml,
                      yamlText: String = "originRestrictionCookie: \"originRestrictionCookie\"\n",
                      adminPort: Int = 0,
                      additionalServices: Map[String, Service] = Map.empty
                     ) extends StyxBaseConfig {
  override def startServer(backendsRegistry: Registry[com.hotels.styx.client.applications.BackendService]): StyxServer = {
    StyxConfig.startServer(this, backendsRegistry)
  }
}

case class StyxYamlConfig(yamlConfig: String,
                          logbackXmlLocation: Path = StyxBaseConfig.defaultLogbackXml
                         ) extends StyxBaseConfig {
  override def startServer(backendsRegistry: Registry[com.hotels.styx.client.applications.BackendService]): StyxServer = {
    val config: YamlConfig = new YamlConfig(yamlConfig)
    val styxConfig = new com.hotels.styx.StyxConfig(yamlConfig)
    val styxServer = new StyxServerBuilder(styxConfig)
      .logConfigLocation(logbackXmlLocation.toString).build()
    styxServer.startAsync().awaitRunning()
    styxServer
  }
}

object StyxBaseConfig {
  val defaultLogbackXml = ResourcePaths.fixturesHome(this.getClass, "/conf/logback/logback.xml")
}

object StyxConfig {

  private def adminPort(config: StyxConfig) = if (config.adminPort == 0) freePort() else config.adminPort

  private def httpConnectorWithPort(config: HttpConnectorConfig) = if (config != null && config.port == 0) {
    config.copy(port = freePort())
  } else {
    config
  }

  private def httpsConnectorWithPort(config: HttpsConnectorConfig) = if (config != null && config.port == 0) {
    config.copy(port = freePort())
  } else {
    config
  }

  def startServer(config: StyxConfig, backendsRegistry: Registry[com.hotels.styx.client.applications.BackendService]): StyxServer = {

    val newAdminPort = adminPort(config)
    val newHttpConnectorConfig = httpConnectorWithPort(config.proxyConfig.connectors.httpConnectorConfig)
    val newHttpsConnectorConfig = httpsConnectorWithPort(config.proxyConfig.connectors.httpsConnectorConfig)

    val proxyConfig = config.proxyConfig.copy(connectors = Connectors(newHttpConnectorConfig, newHttpsConnectorConfig))

    val proxyConfigBuilder = new ProxyServerConfig.Builder()
      .setConnectors(proxyConfig.connectors.asJava)
      .setBossThreadsCount(proxyConfig.bossThreadCount)
      .setClientWorkerThreadsCount(proxyConfig.workerThreadsCount)
      .setKeepAliveTimeoutMillis(proxyConfig.keepAliveTimeoutMillis)
      .setMaxChunkSize(proxyConfig.maxChunkSize)
      .setMaxConnectionsCount(proxyConfig.maxConnectionsCount)
      .setMaxContentLength(proxyConfig.maxContentLength)
      .setMaxHeaderSize(proxyConfig.maxHeaderSize)
      .setMaxInitialLineLength(proxyConfig.maxInitialLineLength)
      .setNioAcceptorBacklog(proxyConfig.nioAcceptorBacklog)
      .setNioKeepAlive(proxyConfig.nioKeepAlive)
      .setNioReuseAddress(proxyConfig.nioReuseAddress)
      .setTcpNoDelay(proxyConfig.tcpNoDelay)
      .setRequestTimeoutMillis(proxyConfig.requestTimeoutMillis)
      .setClientWorkerThreadsCount(proxyConfig.clientWorkerThreadsCount)

    val styxConfig = newStyxConfig(config.yamlText,
      proxyConfigBuilder,
      newAdminServerConfigBuilder(newHttpConnConfig(newAdminPort))
    )

    val services = if (config.additionalServices.nonEmpty) {
      config.additionalServices
    } else {
      Map("backendServiceRegistry" -> backendsRegistry)
    }

    val styxServerBuilder = newStyxServerBuilder(styxConfig, backendsRegistry, config.plugins)
      .additionalServices(services.asJava)
      .logConfigLocation(config.logbackXmlLocation.toString)

    val styxServer = styxServerBuilder.build()
    styxServer.startAsync().awaitRunning()
    styxServer
  }
}

