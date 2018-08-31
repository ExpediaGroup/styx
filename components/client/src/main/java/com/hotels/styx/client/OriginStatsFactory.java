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
package com.hotels.styx.client;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.client.applications.OriginStats;
import com.hotels.styx.client.applications.metrics.OriginMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;


/**
 * A factory that creates {@link OriginStats} instances using a metric registry it wraps. If an {@link OriginStats} already
 * exists for an origin, the same instance will be returned again.
 */
public class OriginStatsFactory {
    private final ConcurrentMap<Origin, OriginMetrics> metricsByOrigin = new ConcurrentHashMap<>();
    private final MetricRegistry metricRegistry;

    /**
     * Constructs a new instance.
     *
     * @param metricRegistry a metric registry
     */
    public OriginStatsFactory(MetricRegistry metricRegistry) {
        this.metricRegistry = requireNonNull(metricRegistry);
    }

    /**
     * Construct a new {@link OriginStats} for an origin, or return a previously created one if it exists.
     *
     * @param origin origin to collect stats for
     * @return the {@link OriginStats}
     */
    public OriginStats originStats(Origin origin) {
        return metricsByOrigin.computeIfAbsent(origin, theOrigin -> OriginMetrics.create(theOrigin, metricRegistry));
    }
}
