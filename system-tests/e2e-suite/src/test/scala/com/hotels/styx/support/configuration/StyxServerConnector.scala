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

import java.util.concurrent.TimeUnit

import com.hotels.styx.support.configuration.HttpsConnectorConfig.defaultHttpsConfig
import io.netty.handler.ssl.SslProvider._

import scala.collection.JavaConverters._

sealed trait StyxServerConnector

case class HttpConnectorConfig(port: Int = 0) extends StyxServerConnector {
  def asJava: com.hotels.styx.server.HttpConnectorConfig = new com.hotels.styx.server.HttpConnectorConfig(port)
}

case class HttpsConnectorConfig(port: Int = 0,
                                sslProvider: String = defaultHttpsConfig.sslProvider(),
                                certificateFile: String = defaultHttpsConfig.certificateFile(),
                                certificateKeyFile: String = defaultHttpsConfig.certificateKeyFile(),
                                cipherSuites: Seq[String] = defaultHttpsConfig.ciphers().asScala.toSeq,
                                sessionTimeoutMillis: Long = defaultHttpsConfig.sessionTimeoutMillis(),
                                sessionCacheSize: Long = defaultHttpsConfig.sessionCacheSize(),
                                protocols: Seq[String] = defaultHttpsConfig.protocols().asScala
                               ) extends StyxServerConnector {
  def asJava: com.hotels.styx.server.HttpsConnectorConfig = new com.hotels.styx.server.HttpsConnectorConfig.Builder()
    .port(port)
    .sslProvider(sslProvider)
    .certificateFile(certificateFile)
    .certificateKeyFile(certificateKeyFile)
    .cipherSuites(cipherSuites.asJava)
    .sessionTimeout(sessionTimeoutMillis, TimeUnit.MILLISECONDS)
    .sessionCacheSize(sessionCacheSize)
    .protocols(protocols.toArray:_*)
    .build()
}

object HttpsConnectorConfig {
  val defaultHttpsConfig = new com.hotels.styx.server.HttpsConnectorConfig.Builder().sslProvider(JDK.toString).build()

  def selfSigned(httpsPort: Int) = new HttpsConnectorConfig(
    httpsPort,
    defaultHttpsConfig.sslProvider(),
    null,
    null,
    null,
    defaultHttpsConfig.sessionTimeoutMillis(),
    defaultHttpsConfig.sessionCacheSize())
}
