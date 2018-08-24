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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.service.HealthCheckConfig;
import com.hotels.styx.client.healthcheck.monitors.AnomalyExcludingOriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.monitors.ScheduledOriginHealthStatusMonitor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Factory that produces {@link OriginHealthStatusMonitor}s based on configuration.
 */
public final class OriginHealthStatusMonitorFactory implements OriginHealthStatusMonitor.Factory {
    /**
     * Create a new monitor, given an application ID, health-check configuration and a supplier providing a function for the health check.
     *
     * @param id                application ID
     * @param healthCheckConfig health check configuration
     * @param supplier          function supplier
     * @return a new monitor
     */
    @Override
    public OriginHealthStatusMonitor create(Id id, HealthCheckConfig healthCheckConfig, Supplier<OriginHealthCheckFunction> supplier) {
        if (healthCheckConfig == null || !healthCheckConfig.isEnabled()) {
            return new NoOriginHealthStatusMonitor();
        }

        ScheduledExecutorService executorService = newScheduledThreadPool(1, new ThreadFactoryBuilder()
                .setNameFormat(format("STYX-ORIGINS-MONITOR-%s", requireNonNull(id)))
                .setDaemon(true)
                .build());

        ScheduledOriginHealthStatusMonitor healthStatusMonitor = new ScheduledOriginHealthStatusMonitor(
                executorService,
                supplier.get(),
                new Schedule(healthCheckConfig.intervalMillis(), MILLISECONDS));

        return new AnomalyExcludingOriginHealthStatusMonitor(healthStatusMonitor, healthCheckConfig.healthyThreshold(), healthCheckConfig.unhealthyThreshold());
    }

}
