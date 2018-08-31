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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Announcer;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.Schedule;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * An {@link com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor} that monitors the origins state
 * periodically.
 */
@ThreadSafe
public class ScheduledOriginHealthStatusMonitor extends AbstractStyxService implements OriginHealthStatusMonitor {
    private final Announcer<OriginHealthStatusMonitor.Listener> listeners = Announcer.to(OriginHealthStatusMonitor.Listener.class);

    private final ScheduledExecutorService hostHealthMonitorExecutor;
    private final OriginHealthCheckFunction healthCheckingFunction;
    private final Schedule schedule;
    private final Set<Origin> origins;

    /**
     * Construct an instance.
     *
     * @param hostHealthMonitorExecutor service that will execute health-checks on a schedule
     * @param healthCheckingFunction function that performs health-checks
     * @param schedule schedule to follow for health-checking
     */
    public ScheduledOriginHealthStatusMonitor(ScheduledExecutorService hostHealthMonitorExecutor, OriginHealthCheckFunction healthCheckingFunction,
                                              Schedule schedule) {
        super("ScheduledOriginHealthStatusMonitor");
        this.hostHealthMonitorExecutor = requireNonNull(hostHealthMonitorExecutor);
        this.healthCheckingFunction = requireNonNull(healthCheckingFunction);
        this.schedule = requireNonNull(schedule);
        this.origins = new ConcurrentSkipListSet<>();
    }

    @VisibleForTesting
    OriginHealthStatusMonitor monitor(Origin... origins) {
        return monitor(copyOf(origins));
    }

    @Override
    public OriginHealthStatusMonitor monitor(Set<Origin> origins) {
        this.origins.addAll(origins);
        if (status() == RUNNING) {
            healthCheck(origins);
        }
        return this;
    }

    @Override
    public OriginHealthStatusMonitor addOriginStatusListener(OriginHealthStatusMonitor.Listener listener) {
        this.listeners.addListener(listener);
        return this;
    }

    @Override
    public OriginHealthStatusMonitor stopMonitoring(Set<Origin> origins) {
        resetListeners(origins);
        this.origins.removeAll(origins);
        return this;
    }

    private void resetListeners(Set<Origin> origins) {
        for (Origin origin : origins) {
            this.listeners.announce().monitoringEnded(origin);
        }
    }

    @Override
    protected CompletableFuture<Void> startService() {
        scheduleHealthCheck();
        return completedFuture(null);
    }

    @Override
    protected CompletableFuture<Void> stopService() {
        this.hostHealthMonitorExecutor.shutdown();
        return completedFuture(null);
    }

    private void scheduleHealthCheck() {
        this.hostHealthMonitorExecutor.scheduleAtFixedRate(() ->
                healthCheck(origins), schedule.initialDelay(), schedule.period(), schedule.unit());
    }

    private void healthCheck(Set<Origin> origins) {
        for (Origin origin : origins) {
            healthCheckOriginAndAnnounceListeners(origin);
        }
    }

    private void healthCheckOriginAndAnnounceListeners(Origin origin) {
        healthCheckingFunction.check(origin, state -> {
            switch (state) {
                case HEALTHY:
                    announceOriginHealthy(origin);
                    break;
                case UNHEALTHY:
                    announceOriginUnhealthy(origin);
                    break;
            }
        });
    }

    private void announceOriginHealthy(Origin origin) {
        listeners.announce().originHealthy(origin);
    }

    private void announceOriginUnhealthy(Origin origin) {
        this.listeners.announce().originUnhealthy(origin);
    }
}
