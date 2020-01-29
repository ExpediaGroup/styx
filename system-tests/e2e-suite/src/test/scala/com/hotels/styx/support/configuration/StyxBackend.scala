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
package com.hotels.styx.support.configuration

import java.util.concurrent.TimeUnit

import com.hotels.styx.api.extension
import com.hotels.styx.server.HttpServer
import com.hotels.styx.servers.MockOriginServer
import com.hotels.styx.support.configuration.BackendsCommon.toOrigin
import com.hotels.styx.support.server.FakeHttpServer

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.Duration

trait ImplicitOriginConversions {
  implicit def fakeserver2Origin(fakeServer: FakeHttpServer): Origin = Origin(
    id = fakeServer.originId(),
    appId = fakeServer.appId(),
    host = "localhost",
    port = fakeServer.port())

  implicit def mockOrigin2Origin(server: MockOriginServer): Origin = Origin(
    id = server.originId(),
    appId = server.appId(),
    host = "localhost",
    port = server.port())

  implicit def httpServer2Origin(httpServer: HttpServer): Origin = Origin(
    host = "localhost", port = httpServer.inetAddress().getPort
  )

  implicit def java2ScalaOrigin(origin: com.hotels.styx.api.extension.Origin): Origin = Origin.fromJava(origin)
}


case class Origins(origins: Origin*)

sealed trait StyxBackend {
  val appId: String
  val origins: Origins
  val responseTimeout: Duration
  val connectionPoolConfig: ConnectionPoolSettings
  val healthCheckConfig: HealthCheckConfig
  val stickySessionConfig: StickySessionConfig

  def toBackend(path: String): BackendService
}

object BackendsCommon {
  def toOrigin(appId: String)(origin: Origin) = {
    if (origin.id.contains("anonymous-origin")) {
      origin.copy(
        id = origin.hostAsString,
        appId = appId
      )
    } else {
      origin.copy(appId = appId)
    }
  }
}

class HttpBackend(override val appId: String,
                  override val origins: Origins,
                  override val responseTimeout: Duration,
                  override val connectionPoolConfig: ConnectionPoolSettings,
                  override val healthCheckConfig: HealthCheckConfig,
                  override val stickySessionConfig: StickySessionConfig
                 ) extends StyxBackend {
  override def toBackend(path: String) = BackendService(appId, path, origins, connectionPoolConfig, healthCheckConfig, stickySessionConfig, responseTimeout, tlsSettings = None)

}

object HttpBackend {
  private val dontCare = Origin("localhost", 0)
  private val defaults = BackendService()
  private val defaultResponseTimeout = defaults.responseTimeout

  def apply(appId: String,
            origins: Origins,
            responseTimeout: Duration = defaultResponseTimeout,
            connectionPoolConfig: ConnectionPoolSettings = ConnectionPoolSettings(),
            healthCheckConfig: HealthCheckConfig = HealthCheckConfig(None),
            stickySessionConfig: StickySessionConfig = StickySessionConfig()
           ): HttpBackend = {
    val originsWithId = origins.origins.map(toOrigin(appId))
    new HttpBackend(appId, Origins(originsWithId: _*), responseTimeout, connectionPoolConfig, healthCheckConfig, stickySessionConfig)
  }
}

class HttpsBackend(override val appId: String,
                   override val origins: Origins,
                   override val responseTimeout: Duration,
                   override val connectionPoolConfig: ConnectionPoolSettings,
                   override val healthCheckConfig: HealthCheckConfig,
                   override val stickySessionConfig: StickySessionConfig,
                   val tlsSettings: TlsSettings
                  ) extends StyxBackend {
  override def toBackend(path: String) = BackendService(appId, path, origins, connectionPoolConfig, healthCheckConfig, stickySessionConfig, responseTimeout, tlsSettings = Some(tlsSettings))

}

object HttpsBackend {
  private val dontCare = Origin("localhost", 0)
  private val defaults = BackendService()
  private val defaultResponseTimeout = defaults.responseTimeout

  def apply(appId: String,
            origins: Origins,
            tlsSettings: TlsSettings,
            responseTimeout: Duration = defaultResponseTimeout,
            connectionPoolConfig: ConnectionPoolSettings = ConnectionPoolSettings(),
            healthCheckConfig: HealthCheckConfig = HealthCheckConfig(None),
            stickySessionConfig: StickySessionConfig = StickySessionConfig()
           ): HttpsBackend = {
    val originsWithId = origins.origins.map(toOrigin(appId))
    new HttpsBackend(appId, Origins(originsWithId: _*), responseTimeout, connectionPoolConfig, healthCheckConfig, stickySessionConfig, tlsSettings)
  }
}

import scala.concurrent.duration._

case class BackendService(appId: String = "generic-app",
                          path: String = "/",
                          origins: Origins = Origins(),
                          connectionPoolConfig: ConnectionPoolSettings = ConnectionPoolSettings(),
                          healthCheckConfig: HealthCheckConfig = HealthCheckConfig(None),
                          stickySessionConfig: StickySessionConfig = StickySessionConfig(),
                          responseTimeout: Duration = 35.seconds,
                          maxHeaderSize: Int = 8192,
                          tlsSettings: Option[TlsSettings] = None
                         ) {
  def asJava: extension.service.BackendService = {
    new extension.service.BackendService.Builder()
      .id(appId)
      .path(path)
      .origins(origins.origins.map(_.asJava()).toSet.asJava)
      .connectionPoolConfig(connectionPoolConfig.asJava)
      .healthCheckConfig(healthCheckConfig.asJava)
      .stickySessionConfig(stickySessionConfig.asJava)
      .responseTimeoutMillis(responseTimeout.toMillis.toInt)
      .https(tlsSettings.map(_.asJava).orNull)
      .maxHeaderSize(maxHeaderSize)
      .build()
  }
}

object BackendService {
  def fromJava(from: extension.service.BackendService): BackendService = {
    val config: extension.service.ConnectionPoolSettings = from.connectionPoolConfig()

    BackendService(
      appId = from.id().toString,
      path = from.path(),
      origins = Origins(from.origins().asScala.map(Origin.fromJava).toSeq: _*),
      connectionPoolConfig = ConnectionPoolSettings.fromJava(config),
      healthCheckConfig = HealthCheckConfig.fromJava(from.healthCheckConfig()),
      stickySessionConfig = StickySessionConfig.fromJava(from.stickySessionConfig()),
      responseTimeout = Duration(from.responseTimeoutMillis(), TimeUnit.MILLISECONDS),
      tlsSettings = from.tlsSettings().asScala.map(TlsSettings.fromJava))
  }
}
