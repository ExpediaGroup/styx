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
package com.hotels.styx.metrics

import org.slf4j.LoggerFactory
import org.slf4j.LoggerFactory.getLogger
import java.util.EnumMap

/**
 * Contains timers for the purposes contained within the [TimerPurpose] enum.
 * These are intended to be passed around in context objects that implement the [TimeMeasurable] interface.
 *
 * This facilitates measurements where the start and end of timing occurs in different parts of the code.
 */
class ContextualTimers {
    private val stoppers = EnumMap<TimerPurpose, TimerMetric.Stopper>(TimerPurpose::class.java)

    /**
     * Start timing the metric encapsulated by the [TimerPurpose].
     *
     * @param centralisedMetrics core Styx metrics
     * @param timerPurpose encapsulates the relevant metric
     */
    fun startTiming(centralisedMetrics: CentralisedMetrics, timerPurpose: TimerPurpose) {
        // There is a bug wherein this function is sometimes called more than once for the same TimerPurpose in the same Context.
        // As we do not yet know what causes this, we are just going to log when it happens.
        if (!stoppers.containsKey(timerPurpose)) {
            stoppers[timerPurpose] = timerPurpose.timerMetric(centralisedMetrics).startTiming()
        } else {
            logger.warn("Attempted to start timing '$timerPurpose', but timing has already begun for '$timerPurpose' in this Context.")
        }
    }

    /**
     * Stop timing the metric encapsulated by the [TimerPurpose].
     *
     * @param timerPurpose encapsulates the relevant metric
     */
    fun stopTiming(timerPurpose: TimerPurpose) {
        stoppers[timerPurpose]?.stop()
    }

    companion object {
        private val logger = getLogger(ContextualTimers::class.java)
    }
}

/**
 * Encapsulates the metrics that [ContextualTimers] can be used for.
 *
 * As it is not appropriate for every metric, this only contains the subset of metrics for which it makes sense.
 */
enum class TimerPurpose(val timerMetric: CentralisedMetrics.() -> TimerMetric) {
    REQUEST_PROCESSING({ proxy.requestProcessingLatency }),
    RESPONSE_PROCESSING({ proxy.responseProcessingLatency })
}

/**
 * An interface implemented by a context object that contains timers. Used to pass timers through the flow of Styx proxying.
 */
interface TimeMeasurable {
    val timers: ContextualTimers?
}
