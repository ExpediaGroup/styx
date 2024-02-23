/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.client

import com.hotels.styx.api.HttpVersion
import com.hotels.styx.api.HttpVersion.HTTP_1_1
import com.hotels.styx.api.HttpVersion.HTTP_2
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings
import reactor.netty.http.HttpProtocol
import reactor.netty.http.HttpProtocol.H2
import reactor.netty.http.HttpProtocol.HTTP11
import reactor.netty.http.client.Http2AllocationStrategy
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

class ReactorConnectionPool(
    connectionPoolSettings: ConnectionPoolSettings = defaultConnectionPoolSettings(),
    private val httpVersion: HttpVersion = HTTP_1_1,
    private val backendService: BackendService = BackendService.Builder().build(),
) {
    val pendingAcquireTimeoutMillis: Int = connectionPoolSettings.pendingConnectionTimeoutMillis()
    val connectTimeoutMillis: Int = connectionPoolSettings.connectTimeoutMillis()
    val maxConnections = connectionPoolSettings.maxConnectionsPerHost()
    val maxIdleTimeMillis: Int = backendService.responseTimeoutMillis()
    val h2MaxConnections = connectionPoolSettings.http2ConnectionPoolSettings().maxConnections ?: DEFAULT_H2_MAX_CONNECTIONS
    val h2MinConnections = connectionPoolSettings.http2ConnectionPoolSettings().minConnections ?: DEFAULT_H2_MIN_CONNECTIONS
    val h2MaxConcurrentStreams =
        connectionPoolSettings.http2ConnectionPoolSettings().maxStreamsPerConnection
            ?: DEFAULT_H2_MAX_STREAMS_PER_CONNECTION
    val pendingAcquireMaxCount: Int =
        if (HTTP_2 == httpVersion) {
            connectionPoolSettings.http2ConnectionPoolSettings().maxPendingStreamsPerHost
                ?: DEFAULT_H2_MAX_PENDING_STREAMS_PER_HOST
        } else {
            connectionPoolSettings.maxPendingConnectionsPerHost()
        }
    val connectionExpirationSeconds: Long = connectionPoolSettings.connectionExpirationSeconds()

    // TODO: needs some investigation on how inflight connections should be shut down
    val disposeTimeoutMillis: Int = backendService.responseTimeoutMillis()

    private val connectionProviderBuilder: ConnectionProvider.Builder = initiateConnectionProvider()

    fun getConnectionProvider(origin: Origin): ConnectionProvider = connectionProviderBuilder.name(origin.id().toString()).build()

    fun supportedHttpProtocols(): Array<HttpProtocol> =
        if (HTTP_2 == httpVersion) {
            arrayOf(H2, HTTP11)
        } else {
            arrayOf(HTTP11)
        }

    fun isHttp2(): Boolean = HTTP_2 == httpVersion

    private fun initiateConnectionProvider(): ConnectionProvider.Builder {
        // Configuration details: https://projectreactor.io/docs/netty/release/reference/index.html#_connection_pool_2
        val builder =
            ConnectionProvider.builder(backendService.id().toString())
                .metrics(true)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMillis.toLong()))
                .disposeTimeout(Duration.ofMillis(disposeTimeoutMillis.toLong()))
                .maxIdleTime(Duration.ofMillis(maxIdleTimeMillis.toLong()))
                .maxLifeTime(Duration.ofSeconds(connectionExpirationSeconds))
                .evictInBackground(Duration.ofSeconds(DEFAULT_CONNECTION_EVICTION_FREQUENCY_SECONDS))
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .lifo()

        if (HTTP_2 == httpVersion) {
            // Increasing the number of max/min connections could result in
            // unnecessary connection creation and cause overheads on concurrency
            val allocationStrategy =
                Http2AllocationStrategy.builder()
                    .maxConcurrentStreams(h2MaxConcurrentStreams.toLong())
                    .maxConnections(h2MaxConnections)
                    .minConnections(h2MinConnections)
                    .build()
            return builder.allocationStrategy(allocationStrategy)
        }

        return builder
    }

    companion object {
        private const val DEFAULT_H2_MAX_CONNECTIONS = 10
        private const val DEFAULT_H2_MIN_CONNECTIONS = 2
        private const val DEFAULT_H2_MAX_STREAMS_PER_CONNECTION = 1024
        private const val DEFAULT_H2_MAX_PENDING_STREAMS_PER_HOST = 200
        private const val DEFAULT_CONNECTION_EVICTION_FREQUENCY_SECONDS = 30L
    }
}
