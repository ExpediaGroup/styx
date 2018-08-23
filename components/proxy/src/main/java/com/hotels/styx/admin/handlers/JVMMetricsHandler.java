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
package com.hotels.styx.admin.handlers;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.codahale.metrics.json.MetricsModule;
import com.google.common.base.Predicate;
import com.hotels.styx.api.MetricRegistry;

import java.time.Duration;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.google.common.collect.Maps.filterKeys;
import static com.google.common.collect.Sets.filter;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Handler for showing the JVM statistics. Can cache page content.
 */
public class JVMMetricsHandler extends JsonHandler<MetricRegistry> {
    private static final Predicate<String> STARTS_WITH_JVM = input -> input.startsWith("jvm");
    private static final boolean DO_NOT_SHOW_SAMPLES = false;

    /**
     * Constructs a new handler.
     *
     * @param metricRegistry  metrics registry
     * @param cacheExpiration duration for which generated page content should be cached
     */
    public JVMMetricsHandler(MetricRegistry metricRegistry, Optional<Duration> cacheExpiration) {
        super(new FilteredRegistry(metricRegistry), cacheExpiration, new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES));
    }

    private static final class FilteredRegistry implements MetricRegistry {
        private final MetricRegistry original;

        public FilteredRegistry(MetricRegistry original) {
            this.original = original;
        }

        @Override
        public MetricRegistry scope(String name) {
            return null;
        }

        @Override
        public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
            return null;
        }

        @Override
        public boolean deregister(String name) {
            return false;
        }

        @Override
        public Counter counter(String name) {
            return null;
        }

        @Override
        public Histogram histogram(String name) {
            return null;
        }

        @Override
        public Meter meter(String name) {
            return null;
        }

        @Override
        public Timer timer(String name) {
            return null;
        }

        @Override
        public void addListener(MetricRegistryListener listener) {
        }

        @Override
        public void removeListener(MetricRegistryListener listener) {
        }

        @Override
        public SortedSet<String> getNames() {
            return filter(original.getNames(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Gauge> getGauges() {
            return filterKeys(original.getGauges(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
            return null;
        }

        @Override
        public SortedMap<String, Counter> getCounters() {
            return filterKeys(original.getCounters(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Counter> getCounters(MetricFilter filter) {
            return null;
        }

        @Override
        public SortedMap<String, Histogram> getHistograms() {
            return filterKeys(original.getHistograms(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
            return null;
        }

        @Override
        public SortedMap<String, Meter> getMeters() {
            return filterKeys(original.getMeters(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Meter> getMeters(MetricFilter filter) {
            return null;
        }

        @Override
        public SortedMap<String, Timer> getTimers() {
            return filterKeys(original.getTimers(), STARTS_WITH_JVM);
        }

        @Override
        public SortedMap<String, Timer> getTimers(MetricFilter filter) {
            return null;
        }
    }
}
