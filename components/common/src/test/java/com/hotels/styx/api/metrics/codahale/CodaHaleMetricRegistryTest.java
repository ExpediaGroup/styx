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
package com.hotels.styx.api.metrics.codahale;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CodaHaleMetricRegistryTest {
    private final Gauge<String> gauge = () -> "someValue";

    private MetricRegistry metricRegistry;
    private MetricRegistryListener listener;

    @BeforeMethod
    public void setUp() {
        this.metricRegistry = new CodaHaleMetricRegistry(new com.codahale.metrics.MetricRegistry());
        this.listener = mock(MetricRegistryListener.class);
    }

    @Test
    public void retrievesPreviouslyRegisteredTimer() {
        Timer timer = metricRegistry.timer("newTimer");

        assertThat(timer, is(notNullValue()));
        assertThat(metricRegistry.timer("newTimer"), is(sameInstance(timer)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwsExceptionIfTryingToCreateTimerWhenOtherMetricExistsWithSameName() {
        metricRegistry.counter("foo");
        metricRegistry.timer("foo");
    }

    @Test
    public void notifiesListenersOnGaugeRegistration() {
        metricRegistry.addListener(listener);
        metricRegistry.register("thing", gauge);
        verify(listener).onGaugeAdded("thing", gauge);
    }

    @Test
    public void notifiesListenersOnScopedGaugeRegistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);
        scopedRegistry.register("thing", gauge);
        verify(listener).onGaugeAdded("one.two.thing", gauge);
    }

    @Test
    public void notifiesListenersOnGaugeRemoval() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);

        scopedRegistry.register("thing", gauge);
        scopedRegistry.deregister("thing");

        verify(listener).onGaugeRemoved("one.two.thing");
    }

    @Test
    public void willNotNotifyListenerOnceRemoved() {
        metricRegistry.addListener(listener);
        metricRegistry.register("thing", gauge);
        verify(listener).onGaugeAdded("thing", gauge);

        metricRegistry.removeListener(listener);

        metricRegistry.register("thing1", gauge);
        verify(listener, never()).onGaugeAdded("thing1", gauge);
    }

    @Test
    public void notifiesListenersOnCounterRegistration() {
        Counter counter = new Counter();
        metricRegistry.addListener(listener);
        metricRegistry.register("counter", counter);

        verify(listener).onCounterAdded("counter", counter);
    }

    @Test
    public void notifiesListenersOnScopedCounterRegistration() {
        Counter counter = new Counter();
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);
        scopedRegistry.register("counter", counter);
        verify(listener).onCounterAdded("one.two.counter", counter);
    }

    @Test
    public void notifiesListenersOnCounterRemoval() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);

        scopedRegistry.register("thing", new Counter());
        scopedRegistry.deregister("thing");

        verify(listener).onCounterRemoved("one.two.thing");
    }

    @Test
    public void notifiesListenersOnHistogramRegistration() {
        metricRegistry.addListener(listener);
        Histogram histogram = metricRegistry.histogram("histogram");
        verify(listener).onHistogramAdded("histogram", histogram);
    }

    @Test
    public void notifiesListenersOnScopedHistogramRegistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);
        Histogram histogram = scopedRegistry.histogram("histogram");
        verify(listener).onHistogramAdded("one.two.histogram", histogram);
    }

    @Test
    public void notifiesListenersOnHistogramDeegistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);

        scopedRegistry.histogram("thing");
        scopedRegistry.deregister("thing");

        verify(listener).onHistogramRemoved("one.two.thing");
    }

    @Test
    public void notifiesListenersOnMeterRegistration() {
        metricRegistry.addListener(listener);
        Meter meter = metricRegistry.meter("meter");
        verify(listener).onMeterAdded("meter", meter);
    }

    @Test
    public void notifiesListenersOnScopedMeterRegistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);
        Meter meter = scopedRegistry.meter("meter");
        verify(listener).onMeterAdded("one.two.meter", meter);
    }

    @Test
    public void notifiesListenersOnMeterDeregistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);

        scopedRegistry.meter("thing");
        scopedRegistry.deregister("thing");

        verify(listener).onMeterRemoved("one.two.thing");
    }

    @Test
    public void notifiesListenersOnTimerRegistration() {
        metricRegistry.addListener(listener);
        Timer timer = metricRegistry.timer("timer");
        verify(listener).onTimerAdded("timer", timer);
    }

    @Test
    public void notifiesListenersOnScopedTimerRegistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);
        Timer timer = scopedRegistry.timer("timer");
        verify(listener).onTimerAdded("one.two.timer", timer);
    }

    @Test
    public void notifiesListenersOnTimerDeregistration() {
        MetricRegistry scopedRegistry = metricRegistry.scope("one").scope("two");
        scopedRegistry.addListener(listener);

        scopedRegistry.timer("thing");
        scopedRegistry.deregister("thing");

        verify(listener).onTimerRemoved("one.two.thing");
    }

    @Test
    public void notifiesNewlyAddedListenerWithPreviouslyRegisteredMetrics() {
        Gauge<String> newGauge = metricRegistry.register("gauge", gauge);
        Counter newCounter = metricRegistry.register("counter", new Counter());

        metricRegistry.addListener(listener);

        verify(listener).onGaugeAdded("gauge", newGauge);
        verify(listener).onCounterAdded("counter", newCounter);
    }

    @Test
    public void createsTimerBackedByLatencyStats() {
        Timer timer = metricRegistry.timer("timer");
        assertThat(timer.getSnapshot(), is(instanceOf(SlidingWindowHistogramReservoir.HistogramSnapshot.class)));
    }
}