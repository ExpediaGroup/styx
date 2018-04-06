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
package com.hotels.styx.admin.handlers;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;

import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Handler for showing all registered metrics for styx server. Can cache page content.
 */
public class MetricsHandler extends JsonHandler<MetricRegistry> {
    private static final boolean DO_NOT_SHOW_SAMPLES = false;

    /**
     * Constructs a new handler.
     *
     * @param metricRegistry  metrics registry
     * @param cacheExpiration duration for which generated page content should be cached
     */
    public MetricsHandler(CodaHaleMetricRegistry metricRegistry, Optional<Duration> cacheExpiration) {
        super(checkNotNull(metricRegistry.getMetricRegistry()), cacheExpiration, new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES));
    }
}
