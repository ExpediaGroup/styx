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
package com.hotels.styx.client.applications.metrics

import com.hotels.styx.api.extension.Origin
import com.hotels.styx.client.applications.OriginStats
import com.hotels.styx.common.SimpleCache
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.TimerMetric
import io.micrometer.core.instrument.Counter

/**
 * Reports metrics about origins to a {@link MetricRegistry}.
 * <p/>
 * This class is not thread-safe. It is intended to be used in a thread-confined manner.
 * Ie, always create a new instance prior using, do not pass between different threads,
 * and lose the reference when no longer needed.
 * <p/>
 * Consider twice before caching. The reference could accidentally being shared by two
 * connections scheduled on different event loops.
 */
class OriginMetrics(metrics: CentralisedMetrics, origin: Origin) : OriginStats {
    private val clientMetrics = metrics.proxy.client
    private var requestSuccessMeter: Counter = clientMetrics.originResponseNot5xx(origin)
    private var requestErrorMeter: Counter = clientMetrics.originResponse5xx(origin)
    private var requestCancellations: Counter = clientMetrics.requestsCancelled(origin)
    private var requestLatency: TimerMetric = clientMetrics.originRequestLatency(origin)
    private var timeToFirstByte: TimerMetric = clientMetrics.timeToFirstByte(origin)
    private var responseStatus: SimpleCache<Int, Counter> = clientMetrics.responsesByStatus(origin)

    override fun requestSuccess() = requestSuccessMeter.increment()

    override fun requestError() = requestErrorMeter.increment()

    override fun responseWithStatusCode(statusCode: Int) = responseStatus[statusCode].increment()

    override fun requestCancelled() = requestCancellations.increment()

    override fun requestLatencyTimer(): TimerMetric = requestLatency

    override fun timeToFirstByteTimer(): TimerMetric = timeToFirstByte
}