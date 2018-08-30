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
package com.hotels.styx.client.healthcheck.monitors;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.healthcheck.AnomalyExcludingOriginHealthEventListener;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * An {@link OriginHealthStatusMonitor} that wraps a {@link ScheduledOriginHealthStatusMonitor} but only propagates
 * events once a configurable number of same-events have been received (i.e. X number of healthy in a row, or X number of unhealthy in a row)
 */
public class AnomalyExcludingOriginHealthStatusMonitor implements OriginHealthStatusMonitor {
    private final OriginHealthStatusMonitor healthStatusMonitor;
    private final int healthyThreshold;
    private final int unhealthyThreshold;

    /**
     * Construct an instance.
     *
     * @param healthStatusMonitor monitor to wrap
     * @param healthyThreshold    minimum number of healthy events that must be received before one will be propagated
     * @param unhealthyThreshold  minimum number of unhealthy events that must be received before one will be propagated
     */
    public AnomalyExcludingOriginHealthStatusMonitor(OriginHealthStatusMonitor healthStatusMonitor, int healthyThreshold, int unhealthyThreshold) {
        this.healthStatusMonitor = requireNonNull(healthStatusMonitor);
        this.healthyThreshold = greaterThanZero(healthyThreshold);
        this.unhealthyThreshold = greaterThanZero(unhealthyThreshold);
    }

    private static int greaterThanZero(int integer) {
        checkArgument(integer > 0, integer + " is < 1");
        return integer;
    }

    @Override
    public CompletableFuture<Void> start() {
        return healthStatusMonitor.start();
    }

    @Override
    public CompletableFuture<Void> stop() {
        return healthStatusMonitor.stop();
    }

    @Override
    public OriginHealthStatusMonitor monitor(Set<Origin> origins) {
        return healthStatusMonitor.monitor(origins);
    }

    @Override
    public OriginHealthStatusMonitor stopMonitoring(Set<Origin> origins) {
        return healthStatusMonitor.stopMonitoring(origins);
    }

    @Override
    public OriginHealthStatusMonitor addOriginStatusListener(OriginHealthStatusMonitor.Listener listener) {
        healthStatusMonitor.addOriginStatusListener(new AnomalyExcludingOriginHealthEventListener(listener, healthyThreshold, unhealthyThreshold));
        return this;
    }
}
