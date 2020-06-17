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
package com.hotels.styx.metrics.reporting.sets;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.netty.buffer.ByteBufAllocatorMetric;

import static com.hotels.styx.api.Metrics.name;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Creates a set of gauges that monitor metrics of netty {#ByteBufAllocatorMetric} instance.
 */
public class NettyAllocatorMetrics implements MeterBinder {
    private final Iterable<Tag> tags;
    private final String namespace;
    private final ByteBufAllocatorMetric metric;

    public NettyAllocatorMetrics(String namespace, ByteBufAllocatorMetric metric) {
        this(namespace, metric, emptyList());
    }

    public NettyAllocatorMetrics(String namespace, ByteBufAllocatorMetric metric, Iterable<Tag> tags) {
        this.namespace = requireNonNull(namespace);
        this.metric = requireNonNull(metric);
        this.tags = tags;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder(name(namespace, "usedDirectMemory"), metric, ByteBufAllocatorMetric::usedDirectMemory)
                .tags(tags)
                .register(registry);

        Gauge.builder(name(namespace, "usedHeapMemory"), metric, ByteBufAllocatorMetric::usedHeapMemory)
                .tags(tags)
                .register(registry);
    }
}
