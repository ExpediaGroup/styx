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
package com.hotels.styx.server;

import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.TimerMetric;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

/**
 * An implementation of request event sink that maintains Styx request statistics.
 */
public class RequestStatsCollector implements RequestProgressListener {
    private final CentralisedMetrics metrics;
    private final TimerMetric latencyTimer;
    private final ConcurrentMap<Object, TimerMetric.Stopper> ongoingRequests = new ConcurrentHashMap<>();

    /**
     * Constructs a collector with a {@link MeterRegistry} to report statistics to.
     *
     * @param metrics a registry to report to
     */
    public RequestStatsCollector(CentralisedMetrics metrics) {
        this.metrics = requireNonNull(metrics);
        metrics.proxy().requestsInProgress().register(ongoingRequests, Map::size);
        this.latencyTimer = metrics.proxy().requestLatency();
    }

    @Override
    public void onRequest(Object requestId) {
        TimerMetric.Stopper previous = this.ongoingRequests.putIfAbsent(requestId, latencyTimer.startTiming());
        if (previous == null) {
            metrics.proxy().server().requestsReceived().increment();
        }
    }

    @Override
    public void onComplete(Object requestId, int responseStatus) {
        TimerMetric.Stopper startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            metrics.proxy().server().responsesByStatus(responseStatus).increment();

            startTime.stop();
        }
    }

    @Override
    public void onTerminate(Object requestId) {
        TimerMetric.Stopper startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            startTime.stop();
        }
    }
}
