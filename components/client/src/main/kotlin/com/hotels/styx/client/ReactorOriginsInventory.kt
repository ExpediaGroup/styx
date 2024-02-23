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

import com.google.common.eventbus.EventBus
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.Id
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.api.extension.service.TlsSettings
import com.hotels.styx.client.HttpConfig.defaultHttpConfig
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor
import com.hotels.styx.common.QueueDrainingEventProcessor
import com.hotels.styx.common.StyxFutures.await
import com.hotels.styx.common.logging.HttpRequestMessageLogger
import com.hotels.styx.metrics.CentralisedMetrics
import io.netty.channel.Channel
import io.netty.handler.ssl.SslContext
import reactor.netty.http.client.HttpClientResponse
import reactor.netty.resources.LoopResources
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.SslProvider.SslContextSpec
import java.net.InetSocketAddress
import java.util.function.Consumer
import javax.annotation.concurrent.ThreadSafe
import javax.net.ssl.SNIHostName

/**
 * A Reactor version inventory of the origins configured for a single application
 */
@ThreadSafe
class ReactorOriginsInventory(
    eventBus: EventBus,
    appId: Id,
    originHealthStatusMonitor: OriginHealthStatusMonitor,
    private val metrics: CentralisedMetrics,
    private val connectionPool: ReactorConnectionPool,
    private val hostClientFactory: ReactorHostHttpClient.Factory,
    private val httpConfig: HttpConfig,
    private val tlsSettings: TlsSettings?,
    private val responseTimeoutMillis: Int,
    private val httpRequestMessageLogger: HttpRequestMessageLogger?,
    private val originStatsFactory: OriginStatsFactory,
    private val eventLoopGroup: LoopResources,
    private val sslContext: SslContext?,
    private val doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)?,
) : OriginsInventory(eventBus, originHealthStatusMonitor, appId, metrics) {
    override val eventQueue: QueueDrainingEventProcessor = QueueDrainingEventProcessor(this, true)

    override fun Origin.toMonitoredOrigin(): OriginsInventory.MonitoredOrigin = MonitoredOrigin(this)

    override fun registerEvent() {
        eventBus.register(this)
    }

    override fun addOriginStatusListener() {
        originHealthStatusMonitor.addOriginStatusListener(this)
    }

    /**
     * A builder for [ReactorOriginsInventory].
     */
    class Builder(val appId: Id) {
        private var originHealthMonitor: OriginHealthStatusMonitor = NoOriginHealthStatusMonitor()
        private var metrics: CentralisedMetrics? = null
        private var eventBus = EventBus()
        private var connectionPool: ReactorConnectionPool = ReactorConnectionPool()
        private var hostClientFactory: ReactorHostHttpClient.Factory = ReactorHostHttpClient
        private var initialOrigins: Set<Origin> = emptySet()
        private var httpConfig: HttpConfig = defaultHttpConfig()
        private var tlsSettings: TlsSettings? = null
        private var responseTimeoutMillis: Int = 60_000
        private var httpRequestMessageLogger: HttpRequestMessageLogger? = null
        private var originStatsFactory: OriginStatsFactory? = null
        private var eventLoopGroup: LoopResources = LoopResources.create("$appId-client")
        private var sslContext: SslContext? = null
        private var doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)? = null

        fun metrics(metrics: CentralisedMetrics) =
            apply {
                this.metrics = metrics
            }

        fun connectionPool(connectionPool: ReactorConnectionPool) =
            apply {
                this.connectionPool = connectionPool
            }

        fun hostClientFactory(hostClientFactory: ReactorHostHttpClient.Factory) =
            apply {
                this.hostClientFactory = hostClientFactory
            }

        fun originHealthMonitor(originHealthMonitor: OriginHealthStatusMonitor) =
            apply {
                this.originHealthMonitor = originHealthMonitor
            }

        fun eventBus(eventBus: EventBus) =
            apply {
                this.eventBus = eventBus
            }

        fun initialOrigins(origins: Set<Origin>) =
            apply {
                initialOrigins = origins.toSet()
            }

        fun httpConfig(httpConfig: HttpConfig) =
            apply {
                this.httpConfig = httpConfig
            }

        fun tlsSettings(tlsSettings: TlsSettings?) =
            apply {
                this.tlsSettings = tlsSettings
            }

        fun responseTimeoutMillis(responseTimeoutMillis: Int) =
            apply {
                this.responseTimeoutMillis = responseTimeoutMillis
            }

        fun httpRequestMessageLogger(httpRequestMessageLogger: HttpRequestMessageLogger?) =
            apply {
                this.httpRequestMessageLogger = httpRequestMessageLogger
            }

        fun originStatsFactory(originStatsFactory: OriginStatsFactory) =
            apply {
                this.originStatsFactory = originStatsFactory
            }

        fun eventLoopGroup(eventLoopGroup: LoopResources) =
            apply {
                this.eventLoopGroup = eventLoopGroup
            }

        fun sslContext(sslContext: SslContext?) =
            apply {
                this.sslContext = sslContext
            }

        fun doOnResponse(doOnResponse: ((HttpClientResponse, HttpInterceptor.Context?) -> Unit)?) =
            apply {
                this.doOnResponse = doOnResponse
            }

        fun build(): ReactorOriginsInventory {
            await(originHealthMonitor.start())
            val originsInventory =
                ReactorOriginsInventory(
                    eventBus,
                    appId,
                    originHealthMonitor,
                    checkNotNull(metrics) { "metrics is required" },
                    connectionPool,
                    hostClientFactory,
                    httpConfig,
                    tlsSettings,
                    responseTimeoutMillis,
                    httpRequestMessageLogger,
                    originStatsFactory ?: OriginStatsFactory.CachingOriginStatsFactory(metrics),
                    eventLoopGroup,
                    sslContext,
                    doOnResponse,
                )
            initialOrigins.takeIf { it.isNotEmpty() }?.apply {
                originsInventory.setOrigins(initialOrigins)
            }
            return originsInventory
        }
    }

    inner class MonitoredOrigin(origin: Origin) : OriginsInventory.MonitoredOrigin(origin) {
        // SNI info must be passed while using HTTP/2
        private val h2SslProvider: Consumer<SslContextSpec>? =
            if (sslContext != null && connectionPool.isHttp2()) {
                Consumer<SslContextSpec> { sslContextSpec ->
                    sslContextSpec.sslContext(sslContext)
                        .serverNames(SNIHostName(tlsSettings?.sniHost ?: origin.host()))
                }
            } else {
                null
            }

        // By default, reactor-netty sends the remote hostname as SNI server name.
        // This is a workaround to avoid reactor-netty injecting SNI host if tlsSettings.sendSni() is false.
        private val h11SslHandler: Consumer<Channel>? =
            if (sslContext != null && !connectionPool.isHttp2()) {
                Consumer<Channel> { channel ->
                    val sslProviderBuilder = SslProvider.builder().sslContext(sslContext)

                    if (tlsSettings?.sendSni() == true) {
                        sslProviderBuilder
                            .serverNames(SNIHostName(tlsSettings.sniHost ?: origin.host()))
                            .build()
                            .addSslHandler(channel, InetSocketAddress(origin.host(), origin.port()), false)
                    } else {
                        sslProviderBuilder
                            .build()
                            .addSslHandler(channel, null, false)
                    }
                }
            } else {
                null
            }

        override val hostClient: ReactorHostHttpClient =
            hostClientFactory.create(
                origin,
                connectionPool,
                httpConfig,
                h2SslProvider,
                h11SslHandler,
                responseTimeoutMillis,
                httpRequestMessageLogger,
                originStatsFactory,
                metrics,
                eventLoopGroup,
                doOnResponse,
            )
    }

    companion object {
        @JvmStatic
        fun newOriginsInventoryBuilder(appId: Id): Builder = Builder(appId)

        @JvmStatic
        fun newOriginsInventoryBuilder(
            metrics: CentralisedMetrics,
            backendService: BackendService,
        ): Builder =
            Builder(backendService.id())
                .metrics(metrics)
                .initialOrigins(backendService.origins())
    }
}
