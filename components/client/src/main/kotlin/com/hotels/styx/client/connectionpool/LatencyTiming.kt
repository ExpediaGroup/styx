package com.hotels.styx.client.connectionpool

import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.TimeMeasurable
import com.hotels.styx.metrics.TimerPurpose.REQUEST_PROCESSING
import com.hotels.styx.metrics.TimerPurpose.RESPONSE_PROCESSING

object LatencyTiming  {
    @JvmStatic
    fun finishRequestTiming(context: HttpInterceptor.Context?) {
        if (context is TimeMeasurable && context.timers != null) {
            context.timers?.stopTiming(REQUEST_PROCESSING)
        }
    }

    @JvmStatic
    fun startResponseTiming(metrics: CentralisedMetrics?, context: HttpInterceptor.Context?) {
        if (metrics != null && context is TimeMeasurable && context.timers != null) {
            context.timers?.startTiming(metrics, RESPONSE_PROCESSING)
        }
    }
}
