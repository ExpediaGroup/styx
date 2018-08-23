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
package com.hotels.styx.api.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistryListener;
import com.hotels.styx.api.MetricRegistry;
import org.testng.annotations.Test;

import static com.hotels.styx.api.metrics.ScopedMetricRegistry.scope;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ScopedMetricRegistryTest {

    @Test
    public void scopesMetricsBeforeDelegatingToUnderlyingMetricRegistry() {
        MetricRegistry metricRegistry = mock(MetricRegistry.class);
        MetricRegistry scopedMetricRegistry = new ScopedMetricRegistry("scope", metricRegistry);

        scopedMetricRegistry.counter("counter");
        verify(metricRegistry).counter("scope.counter");

        scopedMetricRegistry.meter("meter");
        verify(metricRegistry).meter("scope.meter");

        scopedMetricRegistry.histogram("histogram");
        verify(metricRegistry).histogram("scope.histogram");

        scopedMetricRegistry.timer("timer");
        verify(metricRegistry).timer("scope.timer");

        Metric metric = new Counter();
        scopedMetricRegistry.register("register.counter", metric);
        verify(metricRegistry).register("scope.register.counter", metric);

        scopedMetricRegistry.deregister("register.counter");
        verify(metricRegistry).deregister("scope.register.counter");

        MetricRegistryListener listener = mock(MetricRegistryListener.class);

        scopedMetricRegistry.addListener(listener);
        verify(metricRegistry).addListener(listener);

        scopedMetricRegistry.removeListener(listener);
        verify(metricRegistry).removeListener(listener);
    }

    @Test
    public void returnAllScopes() {
        ScopedMetricRegistry metricRegistry = (ScopedMetricRegistry)scope("one", mock(MetricRegistry.class)).scope("two").scope("three");
        assertThat(metricRegistry.scopes(), contains("one", "two", "three"));
    }
}