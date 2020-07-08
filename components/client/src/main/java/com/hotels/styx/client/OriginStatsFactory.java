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
package com.hotels.styx.client;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.applications.RequestStats;
import com.hotels.styx.client.applications.metrics.RequestMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;


/**
 * A factory that creates {@link RequestStats} instances using a metric registry it wraps. If an {@link RequestStats} already
 * exists for an origin, the same instance will be returned again.
 */

public interface OriginStatsFactory {
    RequestStats originStats(Origin origin);

    /**
     * A caching OriginStatsFactory. A newly created OriginStats object is cached,
     * and the cached copy is returned for future invocations.
     */
    class CachingOriginStatsFactory implements OriginStatsFactory {
        private final ConcurrentMap<Origin, RequestMetrics> metricsByOrigin = new ConcurrentHashMap<>();
        private final MeterRegistry meterRegistry;

        /**
         * Constructs a new instance.
         *
         * @param meterRegistry a meter registry
         */
        public CachingOriginStatsFactory(MeterRegistry meterRegistry) {
            this.meterRegistry = requireNonNull(meterRegistry);
        }

        /**
         * Construct a new {@link RequestStats} for an origin, or return a previously created one if it exists.
         *
         * @param origin origin to collect stats for
         * @return the {@link RequestStats}
         */
        public RequestStats originStats(Origin origin) {
            return metricsByOrigin.computeIfAbsent(origin, theOrigin -> new RequestMetrics(meterRegistry, theOrigin.id().toString(), theOrigin.applicationId().toString()));
        }
    }
}
