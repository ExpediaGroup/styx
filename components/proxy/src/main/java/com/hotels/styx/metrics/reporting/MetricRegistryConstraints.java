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
package com.hotels.styx.metrics.reporting;

import com.codahale.metrics.MetricRegistry;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;

/**
 * As the metrics reporters require {@link com.codahale.metrics.MetricRegistry}, this class extracts it from the Styx Metric Registry if possible.
 */
public final class MetricRegistryConstraints {
    private MetricRegistryConstraints() {
    }

    /**
     * If the {@link com.hotels.styx.api.MetricRegistry} is a {@link com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry},
     * extracts and returns the  {@link com.codahale.metrics.MetricRegistry}, otherwise throws an exception.
     *
     * @param environment environment
     * @return codahale metrics registry
     * @throws IllegalStateException if the metrics registry is not codahale-based
     */
    public static MetricRegistry codaHaleMetricRegistry(Environment environment) throws IllegalStateException {
        com.hotels.styx.api.MetricRegistry metricRegistry = environment.metricRegistry();

        if (!(metricRegistry instanceof CodaHaleMetricRegistry)) {
            throw new IllegalStateException("Metric Registry " + metricRegistry.getClass() + " not supported");
        }

        return ((CodaHaleMetricRegistry) metricRegistry).getMetricRegistry();
    }
}
