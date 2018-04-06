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

import com.hotels.styx.StyxServerSupport._
import com.hotels.styx.support.configuration.ProxyConfig.proxyServerDefaults


case class ProxyConfig(connectors: Connectors = Connectors(HttpConnectorConfig(), null),
                       bossThreadCount: Int = proxyServerDefaults.bossThreadsCount(),
                       workerThreadsCount: Int = proxyServerDefaults.workerThreadsCount(),
                       nioAcceptorBacklog: Int = proxyServerDefaults.nioAcceptorBacklog(),
                       tcpNoDelay: Boolean = proxyServerDefaults.tcpNoDelay(),
                       nioReuseAddress: Boolean = proxyServerDefaults.nioReuseAddress(),
                       nioKeepAlive: Boolean = proxyServerDefaults.nioKeepAlive(),
                       maxInitialLineLength: Int = proxyServerDefaults.maxInitialLineLength(),
                       maxHeaderSize: Int = proxyServerDefaults.maxHeaderSize(),
                       maxChunkSize: Int = proxyServerDefaults.maxChunkSize(),
                       maxContentLength: Int = proxyServerDefaults.maxContentLength(),
                       requestTimeoutMillis: Int = proxyServerDefaults.requestTimeoutMillis(),
                       keepAliveTimeoutMillis: Int = proxyServerDefaults.keepAliveTimeoutMillis(),
                       maxConnectionsCount: Int = proxyServerDefaults.maxConnectionsCount(),
                       clientWorkerThreadsCount: Int = proxyServerDefaults.clientWorkerThreadsCount()) {
}


object ProxyConfig {
  val httpConfig = HttpConnectorConfig(0)
  val proxyServerDefaults = newProxyServerConfigBuilder(httpConfig.asJava, null).build()
}

