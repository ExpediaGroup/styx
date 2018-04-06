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
package com.hotels.styx.metrics.reporting.sets;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import io.netty.buffer.ByteBufAllocatorMetric;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.copyOf;
import static com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry.name;
import static java.util.Objects.requireNonNull;

/**
 * Creates a set of gauges that monitor metrics of netty {#ByteBufAllocatorMetric} instance.
 */
public class NettyAllocatorMetricSet implements MetricSet {

    private final String namespace;
    private final ByteBufAllocatorMetric metric;

    public NettyAllocatorMetricSet(String namespace,  ByteBufAllocatorMetric metric) {
        this.namespace = requireNonNull(namespace);
        this.metric = requireNonNull(metric);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> gauges = new HashMap<>();
        gauges.put(name(namespace, "usedDirectMemory"), (Gauge<Long>) metric::usedDirectMemory);
        gauges.put(name(namespace, "usedHeapMemory"), (Gauge<Long>) metric::usedHeapMemory);
        return copyOf(gauges);
    }
}
