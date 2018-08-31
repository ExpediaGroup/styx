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
package com.hotels.styx.support.configuration

import java.nio.file.Path
import java.util

import com.hotels.styx.StyxServerSupport._
import com.hotels.styx.config.Config
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.proxy.plugin.NamedPlugin
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.StyxServer
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.startup.StyxServerComponents

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
  def additionalServices: Map[String, StyxService]
  def plugins: List[NamedPlugin]

  def startServer(backendsRegistry: StyxService): StyxServer

  def startServer(): StyxServer

  def services(backendsRegistry: StyxService): Map[String, StyxService] = if (additionalServices.nonEmpty) {
    this.additionalServices
  } else {
    Map("backendServiceRegistry" -> backendsRegistry)
  }
}

object StyxBaseConfig {
  val defaultLogbackXml = ResourcePaths.fixturesHome(this.getClass, "/conf/logback/logback.xml")
}

case class StyxConfig(proxyConfig: ProxyConfig = ProxyConfig(),
                      plugins: List[NamedPlugin] = List(),
                      logbackXmlLocation: Path = StyxBaseConfig.defaultLogbackXml,
                      yamlText: String = "originRestrictionCookie: \"originRestrictionCookie\"\n",
                      adminPort: Int = 0,
                      additionalServices: Map[String, StyxService] = Map.empty
                     ) extends StyxBaseConfig {

  override def startServer(backendsRegistry: StyxService): StyxServer = {

    val proxyConfig = this.proxyConfig.copy(connectors = Connectors(httpConnectorWithPort(), httpsConnectorWithPort()))

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

    val styxConfig = newStyxConfig(this.yamlText,
      proxyConfigBuilder,
      newAdminServerConfigBuilder(newHttpConnConfig(adminPort))
    )

    val java: util.Map[String, StyxService] = services(backendsRegistry).asJava

    val styxServerBuilder = newCoreConfig(styxConfig, backendsRegistry, this.plugins)
      .additionalServices(java)
      .loggingSetUp(this.logbackXmlLocation.toString)

    val styxServer = new StyxServer(styxServerBuilder.build())
    styxServer.startAsync().awaitRunning()
    styxServer
  }

  override def startServer(): StyxServer = {

    val proxyConfig = this.proxyConfig.copy(connectors = Connectors(httpConnectorWithPort(), httpsConnectorWithPort()))

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

    val styxConfig = newStyxConfig(this.yamlText,
      proxyConfigBuilder,
      newAdminServerConfigBuilder(newHttpConnConfig(adminPort))
    )

    val coreConfig = newCoreConfig(styxConfig, this.plugins)
      .loggingSetUp(this.logbackXmlLocation.toString)

    val styxServer = new StyxServer(coreConfig.build())
    styxServer.startAsync().awaitRunning()
    styxServer
  }

  private def httpConnectorWithPort() = this.proxyConfig.connectors.httpConnectorConfig

  private def httpsConnectorWithPort() = this.proxyConfig.connectors.httpsConnectorConfig
}

case class StyxYamlConfig(yamlConfig: String,
                          logbackXmlLocation: Path = StyxBaseConfig.defaultLogbackXml,
                          additionalServices: Map[String, StyxService] = Map.empty,
                          plugins: List[NamedPlugin] = List()
                         ) extends StyxBaseConfig {

  override def startServer(backendsRegistry: StyxService): StyxServer = {
    val config: YamlConfiguration = Config.config(yamlConfig)
    val styxConfig = new com.hotels.styx.StyxConfig(yamlConfig)

    val styxServer = new StyxServer(new StyxServerComponents.Builder()
      .styxConfig(styxConfig)
      .additionalServices(services(backendsRegistry).asJava)
      .loggingSetUp(logbackXmlLocation.toString)
      .build())

    styxServer.startAsync().awaitRunning()
    styxServer
  }

  override def startServer(): StyxServer = {
    val config: YamlConfiguration = Config.config(yamlConfig)
    val styxConfig = new com.hotels.styx.StyxConfig(yamlConfig)

    val styxServer = new StyxServer(new StyxServerComponents.Builder()
      .styxConfig(styxConfig)
      .loggingSetUp(logbackXmlLocation.toString)
      .build())

    styxServer.startAsync().awaitRunning()
    styxServer
  }
}

