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
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.ConnectionPoolSettings
import com.hotels.styx.api.extension.service.Http2ConnectionPoolSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.netty.http.HttpProtocol.H2
import reactor.netty.http.HttpProtocol.HTTP11
import reactor.netty.http.client.Http2AllocationStrategy
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS

class ReactorConnectionPoolTest : StringSpec() {
    private lateinit var connectionPool: ReactorConnectionPool

    init {
        "getConnectionProvider returns a ConnectionProvider for HTTP/1.1" {
            connectionPool =
                ReactorConnectionPool(
                    CONNECTION_POOL_SETTINGS,
                    HttpVersion.HTTP_1_1,
                    BACKEND_SERVICE,
                )

            val expected =
                ConnectionProvider.builder(ORIGIN.id().toString())
                    .metrics(true)
                    .pendingAcquireTimeout(Duration.ofMillis(CONNECTION_POOL_SETTINGS.pendingConnectionTimeoutMillis().toLong()))
                    .disposeTimeout(Duration.ofMillis(BACKEND_SERVICE.responseTimeoutMillis().toLong()))
                    .maxIdleTime(Duration.ofMillis(BACKEND_SERVICE.responseTimeoutMillis().toLong()))
                    .maxLifeTime(Duration.ofSeconds(CONNECTION_POOL_SETTINGS.connectionExpirationSeconds()))
                    .maxConnections(CONNECTION_POOL_SETTINGS.maxConnectionsPerHost())
                    .pendingAcquireMaxCount(CONNECTION_POOL_SETTINGS.maxPendingConnectionsPerHost())
                    .lifo()
                    .build()

            connectionPool.supportedHttpProtocols() shouldBe arrayOf(HTTP11)
            connectionPool.pendingAcquireMaxCount shouldBe CONNECTION_POOL_SETTINGS.maxPendingConnectionsPerHost()
            connectionPool.connectTimeoutMillis shouldBe CONNECTION_POOL_SETTINGS.connectTimeoutMillis()
            connectionPool.maxConnections shouldBe CONNECTION_POOL_SETTINGS.maxConnectionsPerHost()
            connectionPool.disposeTimeoutMillis shouldBe BACKEND_SERVICE.responseTimeoutMillis()
            connectionPool.maxIdleTimeMillis shouldBe BACKEND_SERVICE.responseTimeoutMillis()
            connectionPool.connectionExpirationSeconds shouldBe CONNECTION_POOL_SETTINGS.connectionExpirationSeconds()
            assertConnectionProvider(connectionPool.getConnectionProvider(ORIGIN), expected)
        }

        "getConnectionProvider returns a ConnectionProvider for HTTP/2" {
            connectionPool =
                ReactorConnectionPool(
                    CONNECTION_POOL_SETTINGS,
                    HttpVersion.HTTP_2,
                    BACKEND_SERVICE,
                )

            val expected =
                ConnectionProvider.builder(ORIGIN.id().toString())
                    .metrics(true)
                    .pendingAcquireTimeout(Duration.ofMillis(CONNECTION_POOL_SETTINGS.pendingConnectionTimeoutMillis().toLong()))
                    .disposeTimeout(Duration.ofMillis(BACKEND_SERVICE.responseTimeoutMillis().toLong()))
                    .maxIdleTime(Duration.ofMillis(BACKEND_SERVICE.responseTimeoutMillis().toLong()))
                    .maxLifeTime(Duration.ofSeconds(CONNECTION_POOL_SETTINGS.connectionExpirationSeconds()))
                    .maxConnections(CONNECTION_POOL_SETTINGS.maxConnectionsPerHost())
                    .pendingAcquireMaxCount(CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxPendingStreamsPerHost!!)
                    .lifo()
                    .allocationStrategy(
                        Http2AllocationStrategy.builder()
                            .maxConcurrentStreams(CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxStreamsPerConnection!!.toLong())
                            .maxConnections(CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxConnections!!)
                            .minConnections(CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().minConnections!!)
                            .build(),
                    )
                    .build()

            connectionPool.supportedHttpProtocols() shouldBe arrayOf(H2, HTTP11)
            connectionPool.pendingAcquireMaxCount shouldBe CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxPendingStreamsPerHost
            connectionPool.connectTimeoutMillis shouldBe CONNECTION_POOL_SETTINGS.connectTimeoutMillis()
            connectionPool.maxConnections shouldBe CONNECTION_POOL_SETTINGS.maxConnectionsPerHost()
            connectionPool.disposeTimeoutMillis shouldBe BACKEND_SERVICE.responseTimeoutMillis()
            connectionPool.maxIdleTimeMillis shouldBe BACKEND_SERVICE.responseTimeoutMillis()
            connectionPool.h2MaxConnections shouldBe CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxConnections
            connectionPool.h2MinConnections shouldBe CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().minConnections
            connectionPool.h2MaxConcurrentStreams shouldBe CONNECTION_POOL_SETTINGS.http2ConnectionPoolSettings().maxStreamsPerConnection
            connectionPool.connectionExpirationSeconds shouldBe CONNECTION_POOL_SETTINGS.connectionExpirationSeconds()
            assertConnectionProvider(connectionPool.getConnectionProvider(ORIGIN), expected)
        }
    }

    private fun assertConnectionProvider(
        actual: ConnectionProvider,
        expected: ConnectionProvider,
    ) {
        actual.name() shouldBe expected.name()
        actual.maxConnections() shouldBe expected.maxConnections()
        actual.maxConnectionsPerHost() shouldBe expected.maxConnectionsPerHost()
    }

    companion object {
        private val ORIGIN = Origin.newOriginBuilder("localhost", 886).build()
        private val CONNECTION_POOL_SETTINGS: ConnectionPoolSettings =
            ConnectionPoolSettings.Builder()
                .connectTimeout(123, MILLISECONDS)
                .pendingConnectionTimeout(543, MILLISECONDS)
                .maxConnectionsPerHost(10)
                .maxPendingConnectionsPerHost(87)
                .http2ConnectionPoolSettings(Http2ConnectionPoolSettings(10, 4, 3, 6))
                .build()
        private val BACKEND_SERVICE: BackendService =
            BackendService.newBackendServiceBuilder()
                .responseTimeoutMillis(777)
                .build()
    }
}
