/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.Version;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

public class CoreMetricsTest {
    private final Version version = new Version("STYX.1.2.3");

    @Test
    public void registersJvmMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        CoreMetricsKt.registerCoreMetrics(new CentralisedMetrics(registry));

        assertThat(registry.find("jvm.uptime").gauges(),
                hasSize(1));

        assertThat(registry.find("proxy.netty.buffers.memory")
                        .tags("allocator", "pooled", "memoryType", "direct")
                        .gauges(),
                hasSize(1));

        assertThat(registry.find("proxy.netty.buffers.memory")
                        .tags("allocator", "pooled", "memoryType", "heap")
                        .gauges(),
                hasSize(1));

        assertThat(registry.find("proxy.netty.buffers.memory")
                        .tags("allocator", "unpooled", "memoryType", "direct")
                        .gauges(),
                hasSize(1));

        assertThat(registry.find("proxy.netty.buffers.memory")
                        .tags("allocator", "unpooled", "memoryType", "heap")
                        .gauges(),
                hasSize(1));
    }

    @Test
    public void registersOperatingSystemMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        CoreMetricsKt.registerCoreMetrics(new CentralisedMetrics(registry));

        List<String> gauges = registry.getMeters()
                .stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());


        assertThat(gauges, hasItems(
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
