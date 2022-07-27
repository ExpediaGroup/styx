/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.server

import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.TimerMetric
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


/**
 * An implementation of request event sink that maintains Styx request statistics.
 *
 * @param metrics a registry to report to
 */
open class RequestStatsCollector(private val metrics: CentralisedMetrics) : RequestProgressListener {
    private val latencyTimer: TimerMetric
    private val ongoingRequests: ConcurrentMap<Any, TimerMetric.Stopper> = ConcurrentHashMap()

    init {
        metrics.proxy.requestsInProgress.register(ongoingRequests) { it.size }
        latencyTimer = metrics.proxy.endToEndRequestLatency
    }

    override fun onRequest(requestId: Any) {
        val previous = ongoingRequests.putIfAbsent(requestId, latencyTimer.startTiming())
        if (previous == null) {
            metrics.proxy.server.requestsReceived.increment()
        }
    }

    override fun onComplete(requestId: Any, responseStatus: Int) {
        val startTime = ongoingRequests.remove(requestId)
        if (startTime != null) {
            metrics.proxy.server.responsesByStatus(responseStatus).increment()
            startTime.stop()
        }
    }

    override fun onTerminate(requestId: Any) {
        val startTime = ongoingRequests.remove(requestId)
        startTime?.stop()
    }
}
