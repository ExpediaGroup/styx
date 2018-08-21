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

import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.HealthCheckConfig;
import com.hotels.styx.api.extension.service.spi.StyxService;

import java.util.EventListener;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Monitors the health of origins.
 */
public interface OriginHealthStatusMonitor extends StyxService {
    /**
     * Factory for creating a health monitor of an origin.
     */
    interface Factory {
        /**
         * Create health monitor of an origin with the given config and health checking function.
         *
         * @param id                the backend service id
         * @param healthCheckConfig configuration of the health check
         * @param supplier          of the actual function which decided if the origin is healthy or not
         * @return the monitor of the origin
         */
        OriginHealthStatusMonitor create(Id id, HealthCheckConfig healthCheckConfig, Supplier<OriginHealthCheckFunction> supplier);
    }

    /**
     * An event listener that receives notifications of origin healthiness, unhealthiness and when monitoring for an origin stops.
     */
    interface Listener extends EventListener {
        /**
         * Called when monitoring for an origin has been stopped.
         *
         * @param origin an origin
         */
        void monitoringEnded(Origin origin);

        /**
         * Called when an origin has been declared healthy.
         *
         * @param origin an origin
         */
        void originHealthy(Origin origin);

        /**
         * Called when an origin has been declared unhealthy.
         *
         * @param origin an origin
         */
        void originUnhealthy(Origin origin);
    }

    /**
     * Registers origins for monitoring.
     *
     * @param origins origins to monitor
     * @return this monitor
     */
    OriginHealthStatusMonitor monitor(Set<Origin> origins);

    /**
     * Deregister origins from monitoring.
     *
     * @param origins origins to stop monitoring
     * @return this monitor
     */
    OriginHealthStatusMonitor stopMonitoring(Set<Origin> origins);

    /**
     * Add a listener to be informed of changes in an origin's state.
     *
     * @param listener a listener
     * @return this monitor
     */
    OriginHealthStatusMonitor addOriginStatusListener(Listener listener);

}
