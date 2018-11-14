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
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates a set of gauges that monitor metrics provided by the JVM regarding the OS.
 */
public final class OperatingSystemMetricSet implements MetricSet {
    private final OperatingSystemMXBean bean;

    public OperatingSystemMetricSet() {
        this.bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Gauge<?>> gauges = new HashMap<>();

        castIfInstance(bean, UnixOperatingSystemMXBean.class).ifPresent(unixBean -> {
            gauges.put("fileDescriptors.max", unixBean::getMaxFileDescriptorCount);
            gauges.put("fileDescriptors.open", unixBean::getOpenFileDescriptorCount);
        });

        gauges.put("cpu.process.load", bean::getProcessCpuLoad);
        gauges.put("cpu.process.time", bean::getProcessCpuTime);
        gauges.put("cpu.system.load", bean::getSystemCpuLoad);

        gauges.put("memory.physical.free", bean::getFreePhysicalMemorySize);
        gauges.put("memory.physical.total", bean::getTotalPhysicalMemorySize);
        gauges.put("memory.virtual.committed", bean::getCommittedVirtualMemorySize);

        gauges.put("swapSpace.free", bean::getFreeSwapSpaceSize);
        gauges.put("swapSpace.total", bean::getTotalSwapSpaceSize);

        // Using the "wrong" type then casting it allows us to avoid writing (Gauge<...>) in every single "put" call above
        return (Map) gauges;
    }

    private static <T> Optional<T> castIfInstance(Object o, Class<T> type) {
        return type.isInstance(o) ? Optional.of(type.cast(o)) : Optional.empty();
    }
}
