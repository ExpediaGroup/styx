/*
  Copyright (C) 2013-2018 Expedia Inc.

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

import com.codahale.metrics.Timer;

/**
 * An aggregate of two timers that starts and stops both simultaneously.
 */
public final class AggregateTimer {
    /**
     * Aggregate timed contexts.
     */
    public static class Stopper {
        private final Timer.Context first;
        private final Timer.Context second;

        /**
         * Constructor.
         *
         * @param first first timer context
         * @param second second timer context
         */
        public Stopper(Timer.Context first, Timer.Context second) {
            this.first = first;
            this.second = second;
        }

        /**
         * Stop both timer contexts.
         */
        public void stopAndRecord() {
            this.first.stop();
            this.second.stop();
        }
    }

    private final Timer requestLatency;
    private final Timer applicationLatency;

    /**
     * Constructor.
     *
     * @param requestLatency a timer
     * @param applicationLatency a timer
     */
    public AggregateTimer(Timer requestLatency, Timer applicationLatency) {
        this.requestLatency = requestLatency;
        this.applicationLatency = applicationLatency;
    }

    public Stopper time() {
        return new Stopper(requestLatency.time(), applicationLatency.time());
    }
}
