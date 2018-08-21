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

import java.util.concurrent.TimeUnit.MILLISECONDS

import com.hotels.styx.api.extension
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_CONNECTIONS_PER_HOST
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_CONNECT_TIMEOUT_MILLIS
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.DEFAULT_CONNECTION_EXPIRATION_SECONDS


case class ConnectionPoolSettings(maxConnectionsPerHost: Int = DEFAULT_MAX_CONNECTIONS_PER_HOST,
                                  maxPendingConnectionsPerHost: Int = DEFAULT_MAX_PENDING_CONNECTIONS_PER_HOST,
                                  connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
                                  pendingConnectionTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
                                  connectionExpirationSeconds: Long = DEFAULT_CONNECTION_EXPIRATION_SECONDS
                               ) {
  def asJava: extension.service.ConnectionPoolSettings = new extension.service.ConnectionPoolSettings.Builder()
      .maxConnectionsPerHost(maxConnectionsPerHost)
      .maxConnectionsPerHost(maxPendingConnectionsPerHost)
      .connectTimeout(connectTimeoutMillis, MILLISECONDS)
      .pendingConnectionTimeout(pendingConnectionTimeoutMillis, MILLISECONDS)
      .connectionExpirationSeconds(connectionExpirationSeconds)
    .build()
}

object ConnectionPoolSettings {
  def fromJava(from: extension.service.ConnectionPoolSettings): ConnectionPoolSettings =
    ConnectionPoolSettings(
      maxConnectionsPerHost = from.maxConnectionsPerHost,
      maxPendingConnectionsPerHost = from.maxPendingConnectionsPerHost,
      connectTimeoutMillis = from.connectTimeoutMillis(),
      pendingConnectionTimeoutMillis = from.pendingConnectionTimeoutMillis,
      connectionExpirationSeconds = from.connectionExpirationSeconds
    )
}
