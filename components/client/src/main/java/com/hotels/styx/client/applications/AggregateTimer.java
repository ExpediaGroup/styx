/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.client.applications;


import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * An aggregate of two timers that starts and stops both simultaneously.
 */
public final class AggregateTimer {
    /**
     * Aggregate timed contexts.
     */
    public class Stopper {
        private final Timer.Sample sample;

        /**
         * Constructor.
         *
         * @param sample timing sample
         */
        public Stopper(Timer.Sample sample) {
            this.sample = sample;
        }

        /**
         * Stop the timing sample and record in both timers.
         */
        public void stopAndRecord() {
            long durationNs = sample.stop(requestTimer);
            applicationTimer.record(durationNs, TimeUnit.NANOSECONDS);
        }
    }

    private final Timer requestTimer;
    private final Timer applicationTimer;

    /**
     * Constructor.
     *
     * @param requestTimer a timer
     * @param applicationTimer a timer
     */
    public AggregateTimer(Timer requestTimer, Timer applicationTimer) {
        this.requestTimer = requestTimer;
        this.applicationTimer = applicationTimer;
    }

    public Stopper time() {
        return new Stopper(Timer.start());
    }
}
