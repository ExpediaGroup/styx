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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBufAllocatorMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

public class NettyAllocatorMetricsTest {

    private ByteBufAllocatorMetric metricUnderTest;

    @BeforeEach
    public void before() {
        metricUnderTest = Mockito.mock(ByteBufAllocatorMetric.class);
    }

    @Test
    public void gaugeReportsDirectMemoryUsageValue() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();

        when(metricUnderTest.usedDirectMemory()).thenReturn(1L);
        new NettyAllocatorMetrics("test-metric", metricUnderTest).bindTo(registry);

        Gauge metric = registry.find("test-metric.usedDirectMemory").gauge();
        assertThat(metric.value(), is(1.0));
    }

    @Test
    public void gaugeReportsHeapMemoryUsageValue() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();

        when(metricUnderTest.usedHeapMemory()).thenReturn(1L);
        new NettyAllocatorMetrics("test-metric", metricUnderTest).bindTo(registry);

        Gauge metric = registry.find("test-metric.usedHeapMemory").gauge();
        assertThat(metric.value(), is(1.0));
    }
}
