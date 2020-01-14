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
package com.hotels.styx.startup;

import com.codahale.metrics.Gauge;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class CoreMetricsTest {
    private final Version version = new Version("STYX.1.2.3");

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

    @Test
    public void registersOperatingSystemMetrics() {
        MetricRegistry metrics = new CodaHaleMetricRegistry();
        CoreMetrics.registerCoreMetrics(version, metrics);

        Map<String, Gauge> gauges = metrics.getGauges();

        assertThat(gauges.keySet(), hasItems(
// Unix system only
//                "os.fileDescriptors.max",
//                "os.fileDescriptors.open",

                "os.process.cpu.load",
                "os.process.cpu.time",
                "os.system.cpu.load",
                "os.memory.physical.free",
                "os.memory.physical.total",
                "os.memory.virtual.committed",
                "os.swapSpace.free",
                "os.swapSpace.total"
        ));
    }
}