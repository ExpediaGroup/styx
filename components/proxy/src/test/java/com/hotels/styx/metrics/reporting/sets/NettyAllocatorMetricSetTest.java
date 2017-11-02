package com.hotels.styx.metrics.reporting.sets;

import com.codahale.metrics.Gauge;
import io.netty.buffer.ByteBufAllocatorMetric;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

public class NettyAllocatorMetricSetTest {

    private ByteBufAllocatorMetric metricUnderTest;

    @BeforeMethod
    public void before() {
        metricUnderTest = Mockito.mock(ByteBufAllocatorMetric.class);
    }

    @Test
    public void gaugeReportsDirectMemoryUsageValue() throws Exception {
        when(metricUnderTest.usedDirectMemory()).thenReturn(1L);
        NettyAllocatorMetricSet metricSet = new NettyAllocatorMetricSet("test-metric", metricUnderTest);

        Gauge<Long> metric = (Gauge<Long>) metricSet.getMetrics().get("test-metric.usedDirectMemory");
        assertThat(metric.getValue(), is(1L));
    }

    @Test
    public void gaugeReportsHeapMemoryUsageValue() throws Exception {
        when(metricUnderTest.usedHeapMemory()).thenReturn(1L);
        NettyAllocatorMetricSet metricSet = new NettyAllocatorMetricSet("test-metric", metricUnderTest);

        Gauge<Long> metric = (Gauge<Long>) metricSet.getMetrics().get("test-metric.usedHeapMemory");
        assertThat(metric.getValue(), is(1L));
    }
}