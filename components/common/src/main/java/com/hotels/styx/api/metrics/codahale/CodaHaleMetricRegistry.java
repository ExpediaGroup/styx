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
package com.hotels.styx.api.metrics.codahale;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.ScopedMetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * A {@link MetricRegistry} that acts as an adapter for Codahale's {@link com.codahale.metrics.MetricRegistry}.
 */
public class CodaHaleMetricRegistry implements MetricRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodaHaleMetricRegistry.class);

    private static final Map<String, ToDoubleFunction<Gauge>> GAUGE_ATTRIBUTES = new HashMap<>();
    private static final Map<String, ToDoubleFunction<com.codahale.metrics.Counting>> COUNTING_ATTRIBUTES = new HashMap<>();
    private static final Map<String, ToDoubleFunction<com.codahale.metrics.Sampling>> SAMPLING_ATTRIBUTES = new HashMap<>();
    private static final Map<String, ToDoubleFunction<com.codahale.metrics.Metered>> METERED_ATTRIBUTES = new HashMap<>();

    static {
        GAUGE_ATTRIBUTES.put("value", gauge -> {
            Object value = gauge.getValue();
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else {
                return Double.NaN;
            }
        });

        COUNTING_ATTRIBUTES.put("count", Counting::getCount);

        SAMPLING_ATTRIBUTES.put("max", h -> h.getSnapshot().getMax());
        SAMPLING_ATTRIBUTES.put("mean", h -> h.getSnapshot().getMean());
        SAMPLING_ATTRIBUTES.put("min", h -> h.getSnapshot().getMin());
        SAMPLING_ATTRIBUTES.put("stddev", h -> h.getSnapshot().getStdDev());
        SAMPLING_ATTRIBUTES.put("p50", h -> h.getSnapshot().getMedian());
        SAMPLING_ATTRIBUTES.put("p75", h -> h.getSnapshot().get75thPercentile());
        SAMPLING_ATTRIBUTES.put("p95", h -> h.getSnapshot().get95thPercentile());
        SAMPLING_ATTRIBUTES.put("p98", h -> h.getSnapshot().get98thPercentile());
        SAMPLING_ATTRIBUTES.put("p99", h -> h.getSnapshot().get99thPercentile());
        SAMPLING_ATTRIBUTES.put("p999", h -> h.getSnapshot().get999thPercentile());

        METERED_ATTRIBUTES.put("count", Metered::getCount);
        METERED_ATTRIBUTES.put("m1_rate", Metered::getOneMinuteRate);
        METERED_ATTRIBUTES.put("m5_rate", Metered::getFiveMinuteRate);
        METERED_ATTRIBUTES.put("m15_rate", Metered::getFifteenMinuteRate);
        METERED_ATTRIBUTES.put("mean_rate", Metered::getMeanRate);
    }

    private static class MetricAndMeters {
        final Metric metric;
        final List<io.micrometer.core.instrument.Meter> meters;

        MetricAndMeters(Metric metric, List<io.micrometer.core.instrument.Meter> meters) {
            this.metric = metric;
            this.meters = meters;
        }

        Metric metric() {
            return metric;
        }

        List<io.micrometer.core.instrument.Meter> meters() {
            return meters;
        }
    }

    private final ConcurrentMap<String, MetricAndMeters> dropwizardMeters = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final Set<MetricRegistryListener> listeners = new HashSet<>();

    public MeterRegistry micrometerRegistry() {
        return registry;
    }

    /**
     * Construct an adapter from an existing codahale registry.
     *
     * @param registry Micrometer {@link io.micrometer.core.instrument.MeterRegistry}
     */
    public CodaHaleMetricRegistry(MeterRegistry registry) {
        this.registry = requireNonNull(registry);
    }

    @Override
    public MetricRegistry scope(String name) {
        return new ScopedMetricRegistry(name, this);
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        if (metric instanceof MetricSet) {
            ((MetricSet) metric).getMetrics().forEach(this::register);
            return metric;
        }

        MetricAndMeters metricAndMeters = dropwizardMeters.computeIfAbsent(name, n -> new MetricAndMeters(metric, new ArrayList<>()));

        if (metricAndMeters.meters().isEmpty()) {
            List<io.micrometer.core.instrument.Meter> meters = metricAndMeters.meters();

            if (metric instanceof com.codahale.metrics.Gauge) {
                meters.addAll(addAttributes(name, (com.codahale.metrics.Gauge) metric, GAUGE_ATTRIBUTES));
            }
            if (metric instanceof com.codahale.metrics.Counting) {
                meters.addAll(this.addAttributes(name, (com.codahale.metrics.Counting) metric, COUNTING_ATTRIBUTES));
            }
            if (metric instanceof com.codahale.metrics.Sampling) {
                meters.addAll(addAttributes(name, (com.codahale.metrics.Sampling) metric, SAMPLING_ATTRIBUTES));
            }
            if (metric instanceof com.codahale.metrics.Metered) {
                meters.addAll(addAttributes(name, (com.codahale.metrics.Metered) metric, METERED_ATTRIBUTES));
            }

            if (meters.isEmpty()) {
                LOGGER.warn("Attempt to register an unknown type of Dropwizard metric for \"{}\" of type {}", name, metric.getClass().getName());
            }

            notifyListenersAdd(listeners, name, metric);
        }

        return metric;
    }

    @Override
    public boolean deregister(String name) {
        MetricAndMeters removed = dropwizardMeters.remove(name);
        if (removed == null) {
            return false;
        }
        removed.meters().forEach(registry::remove);
        notifyListenersRemove(listeners, name, removed.metric());
        return true;
    }

    @Override
    public Counter counter(String name) {
        return getOrAdd(name, Counter.class, Counter::new);
    }

    @Override
    public Histogram histogram(String name) {
        return getOrAdd(name, Histogram.class, () -> new Histogram(new ExponentiallyDecayingReservoir()));
    }

    protected <T extends Metric> T getOrAdd(String name, Class<T> tClass, Supplier<T> supplier) {
        Metric metric = ofNullable(dropwizardMeters.get(name)).map(MetricAndMeters::metric).orElse(null);
        if (metric == null) {
            return register(name, supplier.get());
        } else if (tClass.isInstance(metric)) {
            return (T) metric;
        } else {
            throw new IllegalArgumentException(name + " is already used for a different type of metric");
        }
    }

    @Override
    public Meter meter(String name) {
        return getOrAdd(name, Meter.class, Meter::new);
    }

    @Override
    public Timer timer(String name) {
        return getOrAdd(name, Timer.class, this::newTimer);
    }

    private Timer newTimer() {
        return new SampleCountFromSnapshotTimer(new SlidingWindowHistogramReservoir());
    }

    @Override
    public void addListener(MetricRegistryListener listener) {
        listeners.add(listener);
        dropwizardMeters.forEach((name, metricAndMeters) -> notifyListenersAdd(singleton(listener), name, metricAndMeters.metric()));
    }

    @Override
    public void removeListener(MetricRegistryListener listener) {
        listeners.remove(listener);
    }

    @Override
    public SortedSet<String> getNames() {
        return new TreeSet<>(dropwizardMeters.keySet());
    }

    @Override
    public SortedMap<String, Metric> getMetrics() {
        return getMetrics(Metric.class, MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    private <T extends Metric> SortedMap<String, T> getMetrics(Class<T> tClass, MetricFilter filter) {
        SortedMap<String, T> metrics = new TreeMap<>();
        dropwizardMeters.forEach((name, metricAndMeters) -> {
            Metric metric = metricAndMeters.metric();
            if (tClass.isInstance(metric) && filter.matches(name, metric)) {
                metrics.put(name, (T) metric);
            }
        });
        return metrics;
    }

    @Override
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return getMetrics(Gauge.class, filter);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return getMetrics(Counter.class, filter);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return getMetrics(Histogram.class, filter);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return getMetrics(Meter.class, filter);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return getMetrics(Timer.class, filter);
    }

    private <T> List<io.micrometer.core.instrument.Meter> addAttributes(String name, T metric, Map<String, ToDoubleFunction<T>> attributes) {
        List<io.micrometer.core.instrument.Meter> meters = new ArrayList<>();

        attributes.forEach((attr, func) -> {
            meters.add(io.micrometer.core.instrument.Gauge
                    .builder(name, (T) metric, func)
                    .tags(Tags.of("metricSource", "dropwizard").and("attribute", attr))
                    .register(registry));
        });

        return meters;
    }

    private void notifyListenersAdd(Iterable<MetricRegistryListener> listeners, String name, Metric metric) {
        Consumer<MetricRegistryListener> notifier = l -> {
        };
        if (metric instanceof Gauge) {
            notifier = l -> l.onGaugeAdded(name, (Gauge) metric);
        } else if (metric instanceof Counter) {
            notifier = l -> l.onCounterAdded(name, (Counter) metric);
        } else if (metric instanceof Histogram) {
            notifier = l -> l.onHistogramAdded(name, (Histogram) metric);
        } else if (metric instanceof Meter) {
            notifier = l -> l.onMeterAdded(name, (Meter) metric);
        } else if (metric instanceof Timer) {
            notifier = l -> l.onTimerAdded(name, (Timer) metric);
        }

        listeners.forEach(notifier);
    }

    private void notifyListenersRemove(Iterable<MetricRegistryListener> listeners, String name, Metric metric) {
        Consumer<MetricRegistryListener> notifier = l -> {
        };
        if (metric instanceof Gauge) {
            notifier = l -> l.onGaugeRemoved(name);
        } else if (metric instanceof Counter) {
            notifier = l -> l.onCounterRemoved(name);
        } else if (metric instanceof Histogram) {
            notifier = l -> l.onHistogramRemoved(name);
        } else if (metric instanceof Meter) {
            notifier = l -> l.onMeterRemoved(name);
        } else if (metric instanceof Timer) {
            notifier = l -> l.onTimerRemoved(name);
        }

        listeners.forEach(notifier);
    }

    /**
     * Concatenates elements to form a dotted name, omitting null values and empty strings.
     *
     * @param name     the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} and {@code names} concatenated by dots
     */
    public static String name(String name, String... names) {
        StringBuilder builder = new StringBuilder();
        append(builder, name);
        if (names != null) {
            for (String s : names) {
                append(builder, s);
            }
        }
        return builder.toString();
    }

    /**
     * Concatenates a class name and elements to form a dotted name, omitting null values and empty strings.
     *
     * @param klass    the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code klass} and {@code names} concatenated by dots
     */
    public static String name(Class<?> klass, String... names) {
        return name(klass.getName(), names);
    }

    private static void append(StringBuilder builder, String part) {
        if (part != null && !part.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(part);
        }
    }

    private static class SampleCountFromSnapshotTimer extends Timer {
        public SampleCountFromSnapshotTimer(SlidingWindowHistogramReservoir slidingWindowHistogramReservoir) {
            super(slidingWindowHistogramReservoir);
        }

        @Override
        public long getCount() {
            return (long) getSnapshot().size();
        }
    }
}
