/**
 * Copyright (C) 2013-2018 Expedia Inc.
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

import com.hotels.styx.StyxServerSupport._
import com.hotels.styx.api.service.spi.StyxService
import com.hotels.styx.api.support.HostAndPorts._
import com.hotels.styx.infrastructure.{AbstractRegistry, Registry}
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
  def additionalServices: Map[String, StyxService]
  def plugins: List[NamedPlugin]

  def startServer(backendsRegistry: AbstractRegistry[com.hotels.styx.client.applications.BackendService]): StyxServer

  def services(backendsRegistry: AbstractRegistry[_]): Map[String, StyxService] = if (additionalServices.nonEmpty) {
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

  override def startServer(backendsRegistry: AbstractRegistry[com.hotels.styx.client.applications.BackendService]): StyxServer = {

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

    val styxServerBuilder = newStyxServerBuilder(styxConfig, backendsRegistry, this.plugins)
      .additionalServices(services(backendsRegistry).asJava)
      .logConfigLocation(this.logbackXmlLocation.toString)

    val styxServer = styxServerBuilder.build()
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

  override def startServer(backendsRegistry: AbstractRegistry[com.hotels.styx.client.applications.BackendService]): StyxServer = {
    val config: YamlConfig = new YamlConfig(yamlConfig)
    val styxConfig = new com.hotels.styx.StyxConfig(yamlConfig)

    val styxServer = new StyxServerBuilder(styxConfig)
      .additionalServices(services(backendsRegistry).asJava)
      .logConfigLocation(logbackXmlLocation.toString).build()

    styxServer.startAsync().awaitRunning()
    styxServer
  }
}

