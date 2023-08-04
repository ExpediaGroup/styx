/*
  Copyright (C) 2013-2023 Expedia Inc.

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
import com.hotels.styx.api.Id
import com.hotels.styx.api.extension.Origin
import com.hotels.styx.api.extension.service.BackendService
import com.hotels.styx.client.connectionpool.ConnectionPool
import com.hotels.styx.client.connectionpool.ConnectionPools
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor
import com.hotels.styx.common.QueueDrainingEventProcessor
import com.hotels.styx.common.StyxFutures.await
import com.hotels.styx.metrics.CentralisedMetrics
import javax.annotation.concurrent.ThreadSafe

/**
 * An inventory of the origins configured for a single application.
 */
@ThreadSafe
class StyxOriginsInventory(
    eventBus: EventBus,
    appId: Id,
    originHealthStatusMonitor: OriginHealthStatusMonitor,
    private val hostConnectionPoolFactory: ConnectionPool.Factory,
    private val hostClientFactory: StyxHostHttpClient.Factory,
    metrics: CentralisedMetrics,
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
     * A builder for [StyxOriginsInventory].
     */
    class Builder(val appId: Id) {
        private var originHealthMonitor: OriginHealthStatusMonitor = NoOriginHealthStatusMonitor()
        private var metrics: CentralisedMetrics? = null
        private var eventBus = EventBus()
        private var connectionPoolFactory = ConnectionPools.simplePoolFactory()
        private var hostClientFactory: StyxHostHttpClient.Factory =
            StyxHostHttpClient.Factory { pool: ConnectionPool -> StyxHostHttpClient.create(pool) }
        private var initialOrigins: Set<Origin> = emptySet()

        fun metrics(metrics: CentralisedMetrics): Builder {
            this.metrics = metrics
            return this
        }

        fun connectionPoolFactory(connectionPoolFactory: ConnectionPool.Factory): Builder {
            this.connectionPoolFactory = connectionPoolFactory
            return this
        }

        fun hostClientFactory(hostClientFactory: StyxHostHttpClient.Factory): Builder {
            this.hostClientFactory = hostClientFactory
            return this
        }

        fun originHealthMonitor(originHealthMonitor: OriginHealthStatusMonitor): Builder {
            this.originHealthMonitor = originHealthMonitor
            return this
        }

        fun eventBus(eventBus: EventBus): Builder {
            this.eventBus = eventBus
            return this
        }

        fun initialOrigins(origins: Set<Origin>): Builder {
            initialOrigins = origins.toSet()
            return this
        }

        fun build(): OriginsInventory {
            await(originHealthMonitor.start())
            checkNotNull(metrics) { "metrics is required" }
            val originsInventory = StyxOriginsInventory(
                eventBus,
                appId,
                originHealthMonitor,
                connectionPoolFactory,
                hostClientFactory,
                metrics!!
            )
            originsInventory.setOrigins(initialOrigins)
            return originsInventory
        }
    }

    inner class MonitoredOrigin(origin: Origin) : OriginsInventory.MonitoredOrigin(origin) {
        private val connectionPool: ConnectionPool = hostConnectionPoolFactory.create(origin)
        override val hostClient: StyxHostHttpClient = hostClientFactory.create(connectionPool)

        override fun close() {
            super.close()
            connectionPool.close()
        }
    }

    companion object {
        @JvmStatic
        fun newOriginsInventoryBuilder(appId: Id): Builder = Builder(appId)

        @JvmStatic
        fun newOriginsInventoryBuilder(metrics: CentralisedMetrics, backendService: BackendService): Builder =
            Builder(backendService.id())
                .metrics(metrics)
                .connectionPoolFactory(ConnectionPools.simplePoolFactory(backendService, metrics))
                .initialOrigins(backendService.origins())
    }
}
