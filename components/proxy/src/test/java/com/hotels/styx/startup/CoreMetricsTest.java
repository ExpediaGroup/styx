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
package com.hotels.styx.startup;

import com.codahale.metrics.Gauge;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class CoreMetricsTest {
    private final Version version = new Version("1.2.3");

    @Test
    public void registersVersionMetric() {
        MetricRegistry metrics = new CodaHaleMetricRegistry();
        CoreMetrics.registerCoreMetrics(version, metrics);

        Gauge gauge = metrics.getGauges().get("styx.version.buildnumber");

        assertThat(gauge.getValue(), is(3));
    }

    @Test
    public void registersJvmMetrics() {
        MetricRegistry metrics = new CodaHaleMetricRegistry();
        CoreMetrics.registerCoreMetrics(version, metrics);

        Map<String, Gauge> gauges = metrics.getGauges();

        assertThat(gauges.keySet(), hasItems(
                "jvm.bufferpool.direct.capacity",
                "jvm.bufferpool.direct.count",
                "jvm.bufferpool.direct.used",
                "jvm.bufferpool.mapped.capacity",
                "jvm.bufferpool.mapped.count",
                "jvm.bufferpool.mapped.used",
                "jvm.gc.PS-MarkSweep.count",
                "jvm.gc.PS-MarkSweep.time",
                "jvm.gc.PS-Scavenge.count",
                "jvm.gc.PS-Scavenge.time",
                "jvm.memory.heap.committed",
                "jvm.memory.heap.init",
                "jvm.memory.heap.max",
                "jvm.memory.heap.usage",
                "jvm.memory.heap.used",
                "jvm.memory.non-heap.committed",
                "jvm.memory.non-heap.init",
                "jvm.memory.non-heap.max",
                "jvm.memory.non-heap.usage",
                "jvm.memory.non-heap.used",
                "jvm.memory.pools.Code-Cache.committed",
                "jvm.memory.pools.Code-Cache.init",
                "jvm.memory.pools.Code-Cache.max",
                "jvm.memory.pools.Code-Cache.usage",
                "jvm.memory.pools.Code-Cache.used",
                "jvm.memory.pools.Compressed-Class-Space.committed",
                "jvm.memory.pools.Compressed-Class-Space.init",
                "jvm.memory.pools.Compressed-Class-Space.max",
                "jvm.memory.pools.Compressed-Class-Space.usage",
                "jvm.memory.pools.Compressed-Class-Space.used",
                "jvm.memory.pools.Metaspace.committed",
                "jvm.memory.pools.Metaspace.init",
                "jvm.memory.pools.Metaspace.max",
                "jvm.memory.pools.Metaspace.usage",
                "jvm.memory.pools.Metaspace.used",
                "jvm.memory.pools.PS-Eden-Space.committed",
                "jvm.memory.pools.PS-Eden-Space.init",
                "jvm.memory.pools.PS-Eden-Space.max",
                "jvm.memory.pools.PS-Eden-Space.usage",
                "jvm.memory.pools.PS-Eden-Space.used",
                "jvm.memory.pools.PS-Eden-Space.used-after-gc",
                "jvm.memory.pools.PS-Old-Gen.committed",
                "jvm.memory.pools.PS-Old-Gen.init",
                "jvm.memory.pools.PS-Old-Gen.max",
                "jvm.memory.pools.PS-Old-Gen.usage",
                "jvm.memory.pools.PS-Old-Gen.used",
                "jvm.memory.pools.PS-Old-Gen.used-after-gc",
                "jvm.memory.pools.PS-Survivor-Space.committed",
                "jvm.memory.pools.PS-Survivor-Space.init",
                "jvm.memory.pools.PS-Survivor-Space.max",
                "jvm.memory.pools.PS-Survivor-Space.usage",
                "jvm.memory.pools.PS-Survivor-Space.used",
                "jvm.memory.pools.PS-Survivor-Space.used-after-gc",
                "jvm.memory.total.committed",
                "jvm.memory.total.init",
                "jvm.memory.total.max",
                "jvm.memory.total.used",
                "jvm.netty.pooled-allocator.usedDirectMemory",
                "jvm.netty.pooled-allocator.usedHeapMemory",
                "jvm.netty.unpooled-allocator.usedDirectMemory",
                "jvm.netty.unpooled-allocator.usedHeapMemory",
                "jvm.thread.blocked.count",
                "jvm.thread.count",
                "jvm.thread.daemon.count",
                "jvm.thread.deadlock.count",
                "jvm.thread.deadlocks",
                "jvm.thread.new.count",
                "jvm.thread.runnable.count",
                "jvm.thread.terminated.count",
                "jvm.thread.timed_waiting.count",
                "jvm.thread.waiting.count",
                "jvm.uptime",
                "jvm.uptime.formatted"
        ));
    }
}