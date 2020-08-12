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

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.util.Optional;

import static com.hotels.styx.api.Metrics.name;
import static java.util.Collections.emptyList;

/**
 * Creates a set of gauges that monitor metrics provided by the JVM regarding the OS.
 */
public final class OperatingSystemMetrics implements MeterBinder {
    private static final String NAME_PREFIX = "os";
    private final Iterable<Tag> tags;
    private final OperatingSystemMXBean bean;

    public OperatingSystemMetrics() {
        this(emptyList());
    }

    public OperatingSystemMetrics(Iterable<Tag> tags) {
        this.bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.tags = tags;
    }

    private static <T> Optional<T> castIfInstance(Object o, Class<T> type) {
        return type.isInstance(o) ? Optional.of(type.cast(o)) : Optional.empty();
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        castIfInstance(bean, UnixOperatingSystemMXBean.class).ifPresent(unixBean -> {
            Gauge.builder(name(NAME_PREFIX, "fileDescriptors.max"), unixBean, UnixOperatingSystemMXBean::getMaxFileDescriptorCount)
                    .tags(tags)
                    .register(registry);

            Gauge.builder(name(NAME_PREFIX, "fileDescriptors.open"), unixBean, UnixOperatingSystemMXBean::getOpenFileDescriptorCount)
                    .tags(tags)
                    .register(registry);
        });

        Gauge.builder(name(NAME_PREFIX, "process.cpu.load"), bean, OperatingSystemMXBean::getProcessCpuLoad)
                .tags(tags)
                .register(registry);
        Gauge.builder(name(NAME_PREFIX, "process.cpu.time"), bean, OperatingSystemMXBean::getProcessCpuTime)
                .tags(tags)
                .register(registry);
        Gauge.builder(name(NAME_PREFIX, "system.cpu.load"), bean, OperatingSystemMXBean::getSystemCpuLoad)
                .tags(tags)
                .register(registry);

        Gauge.builder(name(NAME_PREFIX, "memory.physical.free"), bean, OperatingSystemMXBean::getFreePhysicalMemorySize)
                .tags(tags)
                .register(registry);
        Gauge.builder(name(NAME_PREFIX, "memory.physical.total"), bean, OperatingSystemMXBean::getTotalPhysicalMemorySize)
                .tags(tags)
                .register(registry);
        Gauge.builder(name(NAME_PREFIX, "memory.virtual.committed"), bean, OperatingSystemMXBean::getCommittedVirtualMemorySize)
                .tags(tags)
                .register(registry);

        Gauge.builder(name(NAME_PREFIX, "swapSpace.free"), bean, OperatingSystemMXBean::getFreeSwapSpaceSize)
                .tags(tags)
                .register(registry);
        Gauge.builder(name(NAME_PREFIX, "swapSpace.total"), bean, OperatingSystemMXBean::getTotalSwapSpaceSize)
                .tags(tags)
                .register(registry);
    }
}
