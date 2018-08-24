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
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.metrics.reporting.sets.NettyAllocatorMetricSet;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.slf4j.Logger;

import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.Optional;

import static java.lang.String.format;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Core metrics - JVM details, Styx version.
 */
public final class CoreMetrics {
    private static final Logger LOG = getLogger(CoreMetrics.class);

    private CoreMetrics() {
    }

    public static void registerCoreMetrics(Version buildInfo, MetricRegistry metrics) {
        registerVersionMetric(buildInfo, metrics);
        registerJvmMetrics(metrics);
    }

    private static void registerVersionMetric(Version buildInfo, MetricRegistry metrics) {
        Optional<Integer> buildNumber = buildInfo.buildNumber();

        if (buildNumber.isPresent()) {
            registerVersionMetric(metrics, buildNumber.get());
        } else {
            LOG.warn("Could not acquire build number from release version: {}", buildInfo);
        }
    }

    private static void registerVersionMetric(MetricRegistry metricRegistry, Integer buildNumber) {
        Gauge<Integer> versionGauge = () -> buildNumber;

        metricRegistry.scope("styx").register("version.buildnumber", versionGauge);
    }

    private static void registerJvmMetrics(MetricRegistry metricRegistry) {
        RuntimeMXBean runtimeMxBean = getRuntimeMXBean();

        MetricRegistry scoped = metricRegistry.scope("jvm");
        scoped.register("bufferpool", new BufferPoolMetricSet(getPlatformMBeanServer()));
        scoped.register("memory", new MemoryUsageGaugeSet());
        scoped.register("thread", new ThreadStatesGaugeSet());
        scoped.register("gc", new GarbageCollectorMetricSet());
        scoped.register("uptime", (Gauge<Long>) runtimeMxBean::getUptime);
        scoped.register("uptime.formatted", (Gauge<String>) () -> formatTime(runtimeMxBean.getUptime()));
        scoped.register("netty", new NettyAllocatorMetricSet("pooled-allocator", PooledByteBufAllocator.DEFAULT.metric()));
        scoped.register("netty", new NettyAllocatorMetricSet("unpooled-allocator", UnpooledByteBufAllocator.DEFAULT.metric()));
    }

    private static String formatTime(long timeInMilliseconds) {
        Duration duration = Duration.ofMillis(timeInMilliseconds);

        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusHours(duration.toHours()).toMinutes();

        return format("%dd %dh %dm", days, hours, minutes);
    }
}
