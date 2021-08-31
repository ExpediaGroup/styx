/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.client.connectionpool

import com.hotels.styx.api.extension.Origin
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.Deleter

class StatsReportingConnectionPool(connectionPool: ConnectionPool, metrics: CentralisedMetrics) : ConnectionPool by connectionPool {
    private val deleters: Set<Deleter>

    init {
        val stats: ConnectionPool.Stats = connectionPool.stats()
        val origin: Origin = connectionPool.origin

        metrics.proxy.client.run {
            deleters = setOf(
                busyConnections(origin).register { stats.busyConnectionCount() },
                pendingConnections(origin).register { stats.pendingConnectionCount() },
                availableConnections(origin).register { stats.availableConnectionCount() },
                connectionAttempts(origin).register { stats.connectionAttempts() },
                connectionFailures(origin).register { stats.connectionFailures() },
                connectionsClosed(origin).register { stats.closedConnections() },
                connectionsTerminated(origin).register { stats.terminatedConnections() },
                connectionsInEstablishment(origin).register { stats.connectionsInEstablishment() }
            )
        }
    }

    override fun close() {
        super.close()
        deleters.forEach { it.delete() }
    }
}