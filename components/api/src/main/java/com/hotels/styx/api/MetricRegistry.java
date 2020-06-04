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
package com.hotels.styx.api;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.SortedMap;
import java.util.SortedSet;

/**
 * A Styx metrics registry that is based on CodaHale {@link Metric} objects.
 */
public abstract class MetricRegistry extends SimpleMeterRegistry {

    /**
     * Returns or creates a sub-scope of this metric registry.
     *
     * @param name Name for the sub-scope.
     * @return A possibly-new metric registry, whose metrics will be 'children' of this scope.
     */
    public abstract MetricRegistry scope(String name);

    /**
     * Given a {@link com.codahale.metrics.Metric}, registers it under the given name.
     *
     * @param name   the name of the metric
     * @param metric the metric
     * @param <T>    the type of the metric
     * @return {@code metric}
     * @throws IllegalArgumentException if the name is already registered
     */
    public abstract <T extends Metric> T register(String name, T metric) throws IllegalArgumentException;

    /**
     * Removes the metric with the given name.
     *
     * @param name the name of the metric
     * @return whether or not the metric was removed
     */
    public abstract boolean deregister(String name);


    /**
     * Return the {@link com.codahale.metrics.Counter} registered under {@code name} or create
     * a new {@link com.codahale.metrics.Counter} and register it under {@code name}.
     *
     * @param name the name of the metric
     * @return a new {@link com.codahale.metrics.Counter}
     */
    public abstract Counter counter(String name);

    /**
     * Return the {@link com.codahale.metrics.Histogram} registered under {@code name} or create
     * a new {@link com.codahale.metrics.Histogram} and register it under {@code name}.
     *
     * @param name the name of the metric
     * @return a new {@link com.codahale.metrics.Histogram}
     */
    public abstract Histogram histogram(String name);

    /**
     * Return the {@link com.codahale.metrics.Meter} registered under {@code name} or create
     * a new {@link com.codahale.metrics.Meter} and register it under {@code name}.
     *
     * @param name the name of the metric
     * @return a new {@link com.codahale.metrics.Meter}
     */
    public abstract Meter meter(String name);

    /**
     * Return the {@link com.codahale.metrics.Timer} registered under {@code name} or create
     * a new {@link com.codahale.metrics.Timer} and register it under {@code name}.
     *
     * @param name the name of the metric
     * @return a new {@link com.codahale.metrics.Timer}
     */
    public abstract Timer timer(String name);

    /**
     * Adds a {@link MetricRegistryListener} to a collection of listeners that will be notified on
     * metric creation.  Listeners will be notified in the order in which they are added.
     * <p/>
     * <b>N.B.:</b> The listener will be notified of all existing metrics when it first registers.
     *
     * @param listener the listener that will be notified
     */
    public abstract void addListener(MetricRegistryListener listener);

    /**
     * Removes a {@link MetricRegistryListener} from this registry's collection of listeners.
     *
     * @param listener the listener that will be removed
     */
    public abstract void removeListener(MetricRegistryListener listener);

    /**
     * Returns a set of the names of all the metrics in the registry.
     *
     * @return the names of all the metrics
     */
    public abstract SortedSet<String> getNames();

    /**
     * Returns a map of all the gauges in the registry and their names.
     *
     * @return all the gauges in the registry
     */
    public abstract SortedMap<String, Gauge> getGauges();

    /**
     * Returns a map of all the gauges in the registry and their names which match the given filter.
     *
     * @param filter the metric filter to match
     * @return all the gauges in the registry
     */
    public abstract SortedMap<String, Gauge> getGauges(MetricFilter filter);

    /**
     * Returns a map of all the counters in the registry and their names.
     *
     * @return all the counters in the registry
     */
    public abstract SortedMap<String, Counter> getCounters();

    /**
     * Returns a map of all the counters in the registry and their names which match the given
     * filter.
     *
     * @param filter the metric filter to match
     * @return all the counters in the registry
     */
    public abstract SortedMap<String, Counter> getCounters(MetricFilter filter);

    /**
     * Returns a map of all the histograms in the registry and their names.
     *
     * @return all the histograms in the registry
     */
    public abstract SortedMap<String, Histogram> getHistograms();

    /**
     * Returns a map of all the histograms in the registry and their names which match the given
     * filter.
     *
     * @param filter the metric filter to match
     * @return all the histograms in the registry
     */
    public abstract SortedMap<String, Histogram> getHistograms(MetricFilter filter);

    /**
     * Returns a map of all the meters in the registry and their names which match the given filter.
     *
     * @param filter the metric filter to match
     * @return all the meters in the registry
     */
    public abstract SortedMap<String, Meter> getMeters(MetricFilter filter);

    /**
     * Returns a map of all the timers in the registry and their names.
     *
     * @return all the timers in the registry
     */
    public abstract SortedMap<String, Timer> getTimers();

    /**
     * Returns a map of all the timers in the registry and their names which match the given filter.
     *
     * @param filter the metric filter to match
     * @return all the timers in the registry
     */
    public abstract SortedMap<String, Timer> getTimers(MetricFilter filter);


    /**
     * A map of metric names to metrics.
     *
     * @return the metrics
     */
    public abstract SortedMap<String, Metric> getMetrics();

}
