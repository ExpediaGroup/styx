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
package com.hotels.styx.client.netty.connectionpool

import com.hotels.styx.metrics.CentralisedMetrics
import com.hotels.styx.metrics.ContextualTimers
import com.hotels.styx.metrics.TimerPurpose.REQUEST_PROCESSING
import com.hotels.styx.metrics.TimerPurpose.RESPONSE_PROCESSING

/**
 * This only exists to limit how much we need to change code in other classes.
 *
 * If refactoring code, feel free to find a solution that doesn't feature this class.
 */
class TimingHelper(
    val metrics : CentralisedMetrics?,
    val timers : ContextualTimers?
)  {

    fun finishRequestTiming() {
        timers?.stopTiming(REQUEST_PROCESSING)
    }

    fun startResponseTiming() {
        if (metrics != null) {
            timers?.startTiming(metrics, RESPONSE_PROCESSING)
        }
    }
}