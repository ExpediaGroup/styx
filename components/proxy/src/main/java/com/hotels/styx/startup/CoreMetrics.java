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

import com.hotels.styx.Version;
import com.hotels.styx.metrics.reporting.sets.NettyAllocatorMetrics;
import com.hotels.styx.metrics.reporting.sets.OperatingSystemMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.slf4j.Logger;

import java.lang.management.RuntimeMXBean;
import java.util.Optional;

import static com.hotels.styx.api.Metrics.name;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Core metrics - JVM details, Styx version.
 */
public final class CoreMetrics {
    private static final String JVM_METRICS_ROOT = "jvm";
    private static final Logger LOG = getLogger(CoreMetrics.class);

    private CoreMetrics() {
    }

    public static void registerCoreMetrics(Version buildInfo, MeterRegistry registry) {
        registerVersionMetric(buildInfo, registry);
        registerJvmMetrics(registry);
        new OperatingSystemMetrics().bindTo(registry);
    }

    private static void registerVersionMetric(Version buildInfo, MeterRegistry registry) {
        Optional<Integer> buildNumber = buildInfo.buildNumber();

        if (buildNumber.isPresent()) {
            registerVersionMetric(registry, buildNumber.get());
        } else {
            LOG.warn("Could not acquire build number from release version: {}", buildInfo);
        }
    }

    private static void registerVersionMetric(MeterRegistry registry, Integer buildNumber) {
        Counter versionMeter = registry.counter("styx.version", Tags.of("buildnumber", buildNumber.toString()));
        versionMeter.increment();
    }

    private static void registerJvmMetrics(MeterRegistry registry) {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);

        RuntimeMXBean runtimeMxBean = getRuntimeMXBean();
        registry.gauge(name(JVM_METRICS_ROOT, "uptime"), Tags.empty(), runtimeMxBean, RuntimeMXBean::getUptime);

        new NettyAllocatorMetrics(
                name(JVM_METRICS_ROOT, "netty", "pooled-allocator"),
                PooledByteBufAllocator.DEFAULT.metric()
        ).bindTo(registry);

        new NettyAllocatorMetrics(
                name(JVM_METRICS_ROOT, "netty", "unpooled-allocator"),
                UnpooledByteBufAllocator.DEFAULT.metric()
        ).bindTo(registry);
    }
}
