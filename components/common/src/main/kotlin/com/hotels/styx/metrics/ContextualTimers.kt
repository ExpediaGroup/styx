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

import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory.getLogger
import java.util.EnumMap

class ContextualTimers {
    private val stoppers = EnumMap<TimerPurpose, TimerMetric.Stopper>(TimerPurpose::class.java)

    fun startTiming(centralisedMetrics: CentralisedMetrics, timerPurpose: TimerPurpose) {
        println("startTiming($timerPurpose)")
        check(!stoppers.containsKey(timerPurpose))
        stoppers[timerPurpose] = timerPurpose.timerMetric(centralisedMetrics).startTiming()
    }

    fun stopTiming(timerPurpose: TimerPurpose) {
        println("stopTiming($timerPurpose)")
        stoppers[timerPurpose]?.stop()
    }

    companion object {
        private val logger = getLogger(ContextualTimers::class.java)
    }
}

enum class TimerPurpose(val timerMetric: CentralisedMetrics.() -> TimerMetric) {
    REQUEST_PROCESSING({ proxy.requestProcessingLatency }),
    RESPONSE_PROCESSING({ proxy.responseProcessingLatency })
}

interface TimeMeasurable {
    val timers: ContextualTimers?
}
