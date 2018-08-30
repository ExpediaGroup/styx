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
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;

/**
 * A Metric Registry that prefixes all its metrics with the scope.
 */
public class ScopedMetricRegistry implements MetricRegistry {
    private final String scope;
    private final MetricRegistry parent;

    /**
     * Constructs a scoped metric registry with the specified scope and parent registry.
     *
     * @param scope  the scope to use
     * @param parent the metric registry to delegate
     */
    public ScopedMetricRegistry(String scope, MetricRegistry parent) {
        this.scope = requireNonNull(scope);
        this.parent = requireNonNull(parent);
    }

    /**
     * Constructs a scoped metric registry with the specified scope and parent registry.
     *
     * @param scope  the scope to use
     * @param parent the metric registry to delegate
     * @return a new registry
     */
    public static ScopedMetricRegistry scope(String scope, MetricRegistry parent) {
        return new ScopedMetricRegistry(scope, parent);
    }

    @Override
    public MetricRegistry scope(String name) {
        return new ScopedMetricRegistry(name, this);
    }

    /**
     * Returns the list of scopes this registry is part of.
     *
     * @return the list of scopes this registry is part of
     */
    public List<String> scopes() {
        List<String> scopes = newArrayList();
        if (parent instanceof ScopedMetricRegistry) {
            scopes.addAll(((ScopedMetricRegistry) parent).scopes());
        }
        scopes.add(scope);
        return scopes;
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        try {
            return parent.register(scopedName(name), metric);
        } catch (IllegalArgumentException e) {
            // ignore
            return metric;
        }
    }

    @Override
    public boolean deregister(String name) {
        return parent.deregister(scopedName(name));
    }

    @Override
    public Counter counter(String name) {
        return parent.counter(scopedName(name));
    }

    @Override
    public Histogram histogram(String name) {
        return parent.histogram(scopedName(name));
    }

    @Override
    public Meter meter(String name) {
        return parent.meter(scopedName(name));
    }

    @Override
    public Timer timer(String name) {
        return parent.timer(scopedName(name));
    }

    private String scopedName(String name) {
        return scope + "." + name;
    }

    @Override
    public void addListener(MetricRegistryListener listener) {
        this.parent.addListener(listener);
    }

    @Override
    public void removeListener(MetricRegistryListener listener) {
        this.parent.removeListener(listener);
    }

    @Override
    public SortedSet<String> getNames() {
        return this.parent.getNames();
    }

    @Override
    public SortedMap<String, Gauge> getGauges() {
        return this.parent.getGauges();
    }

    @Override
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return this.parent.getGauges(filter);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return this.parent.getCounters();
    }

    @Override
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return this.parent.getCounters(filter);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return this.parent.getHistograms();
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return this.parent.getHistograms(filter);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return this.parent.getMeters();
    }

    @Override
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return this.parent.getMeters(filter);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return this.parent.getTimers();
    }

    @Override
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return this.parent.getTimers(filter);
    }
}
