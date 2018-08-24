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
package com.hotels.styx.client.healthcheck;

import com.hotels.styx.api.extension.Origin;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;


/**
 * An origin status monitor listener that fires originHealthy/originUnhealthy events to another listener once it receives more than a given minimum number of such events itself.
 */
public class AnomalyExcludingOriginHealthEventListener implements OriginHealthStatusMonitor.Listener {
    private final OriginHealthStatusMonitor.Listener listener;
    private final int healthyThreshold;
    private final int unhealthyThreshold;
    private final ConcurrentHashMap<Origin, ConsecutiveEventsCounter> originHealthStatusCounter;

    /**
     * Construct an instance.
     *
     * @param listener listener to propagate events to
     * @param healthyThreshold minimum number of healthy events before a healthy event will be propagated to the wrapped listener
     * @param unhealthyThreshold minimum number of unhealthy events before an unhealthy event will be propagated to the wrapped listener
     */
    public AnomalyExcludingOriginHealthEventListener(OriginHealthStatusMonitor.Listener listener, int healthyThreshold, int unhealthyThreshold) {
        this.listener = requireNonNull(listener);
        this.healthyThreshold = healthyThreshold;
        this.unhealthyThreshold = unhealthyThreshold;
        this.originHealthStatusCounter = new ConcurrentHashMap<>();
    }

    @Override
    public void monitoringEnded(Origin origin) {
        originHealthStatusCounter.remove(origin);
    }

    @Override
    public void originHealthy(Origin origin) {
        ConsecutiveEventsCounter counter = getOrCreateCounterFor(origin);
        if (counter.incrementAndGetOriginUp() >= healthyThreshold) {
            this.listener.originHealthy(origin);
        }
    }

    @Override
    public void originUnhealthy(Origin origin) {
        ConsecutiveEventsCounter counter = getOrCreateCounterFor(origin);
        if (counter.incrementAndGetOriginDown() >= unhealthyThreshold) {
            this.listener.originUnhealthy(origin);
        }
    }

    private ConsecutiveEventsCounter getOrCreateCounterFor(Origin origin) {
        return originHealthStatusCounter.computeIfAbsent(origin, theOrigin -> new ConsecutiveEventsCounter());
    }

    private static class ConsecutiveEventsCounter {
        private int upEvents;
        private int downEvents;

        public synchronized int incrementAndGetOriginUp() {
            downEvents = 0;
            upEvents++;
            return upEvents;
        }

        public synchronized int incrementAndGetOriginDown() {
            upEvents = 0;
            downEvents++;
            return downEvents;
        }
    }
}
