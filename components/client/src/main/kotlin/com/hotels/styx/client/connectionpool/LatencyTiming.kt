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
package com.hotels.styx.client.connectionpool

import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.TimeMeasurable
import com.hotels.styx.metrics.TimerPurpose.REQUEST_PROCESSING
import com.hotels.styx.metrics.TimerPurpose.RESPONSE_PROCESSING

/**
 * Convenience methods for request and response processing latency measurement.
 */
object LatencyTiming  {
    /**
     * Stops the request processing timer, if one is present in the context.
     * Note that this only has an effect if the context is [TimeMeasurable].
     *
     * @param context context that may contain timers
     */
    @JvmStatic
    fun finishRequestTiming(context: HttpInterceptor.Context?) {
        if (context is TimeMeasurable && context.timers != null) {
            context.timers?.stopTiming(REQUEST_PROCESSING)
        }
    }

    /**
     * Starts the response processing timer, if one is present in the context.
     * Note that this only has an effect if the metrics object is non-null and the context is [TimeMeasurable].
     *
     * @param metrics metrics - required to access timers to start them
     * @param context context that can have timers set in it
     */
    @JvmStatic
    fun startResponseTiming(metrics: CentralisedMetrics?, context: HttpInterceptor.Context?) {
        if (metrics != null && context is TimeMeasurable && context.timers != null) {
            context.timers?.startTiming(metrics, RESPONSE_PROCESSING)
        }
    }
}
