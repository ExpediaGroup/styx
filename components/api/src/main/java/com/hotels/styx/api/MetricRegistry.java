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
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.Nullable;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static com.hotels.styx.api.Metrics.name;

/**
 * A Styx metrics registry that is based on CodaHale {@link Metric} objects.
 */
public abstract class MetricRegistry extends MeterRegistry {

    private final MeterRegistry delegate; // TODO: MetricRegistry?

    private Iterable<Tag> scopeTags = Tags.empty();
    private String scopePrefix;

    private final Config config = new Config();
    private final More more = new More();

    public MetricRegistry() {
        this(new SimpleMeterRegistry());
    }

    public MetricRegistry(MeterRegistry delegate) {
        super(delegate.config().clock());
        this.delegate = delegate;
    }

    private String prefix(String name) {
        return name(scopePrefix, name);
    }

    private Iterable<Tag> merge(Iterable<Tag> tags) {
        return Tags.of(scopeTags).and(tags);
    }

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


    /*
     * Delegated methods from MeterRegistry
     */

    @Override
    public List<io.micrometer.core.instrument.Meter> getMeters() {
        return delegate.getMeters(); // TODO: Should this be filtered according to tags and prefix?
    }

    @Override
    public void forEachMeter(Consumer<? super io.micrometer.core.instrument.Meter> consumer) {
        delegate.forEachMeter(consumer); // TODO: Should the meter list be filtered?
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Search find(String name) {
        return delegate.find(name); // TODO: Should the name be scoped? Should we add the tags too?
    }

    @Override
    public RequiredSearch get(String name) {
        return delegate.get(name); // TODO: Should the name be scoped? Should we add the tags too?
    }

    @Override
    public io.micrometer.core.instrument.Counter counter(String name, Iterable<Tag> tags) {
        return delegate.counter(prefix(name), merge(tags));
    }

    @Override
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return delegate.summary(prefix(name), merge(tags));
    }

    @Override
    public io.micrometer.core.instrument.Timer timer(String name, Iterable<Tag> tags) {
        return delegate.timer(prefix(name), merge(tags));
    }

    @Override
    public More more() {
        return more;
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, @Nullable T stateObject, ToDoubleFunction<T> valueFunction) {
        return delegate.gauge(prefix(name), merge(tags), stateObject, valueFunction);
    }

    @Override
    public io.micrometer.core.instrument.Meter remove(io.micrometer.core.instrument.Meter.Id mappedId) {
        return delegate.remove(mappedId); // Assumes that the ID is already mapped.
    }

    @Override
    public void clear() {
        delegate.clear(); // TODO: Just clear according to tags and prefix?
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /*
     * Delegating inner classes from MeterRegistry
     */
    public class Config extends MeterRegistry.Config {

        public Config scopeTags(Iterable<Tag> tags) {
            scopeTags = merge(tags);
            return this;
        }

        public Config scopeTags(String... tags) {
            return scopeTags(Tags.of(tags));
        }

        public Config scopePrefix(String prefix) {
            scopePrefix = prefix;
            return this;
        }

        @Override
        public Config commonTags(Iterable<Tag> tags) {
            delegate.config().commonTags(tags);
            return this;
        }

        @Override
        public synchronized Config meterFilter(MeterFilter filter) {
            delegate.config().meterFilter(filter);
            return this;
        }

        @Override
        public Config onMeterAdded(Consumer<io.micrometer.core.instrument.Meter> meterAddedListener) {
            return this;
        }

        @Override
        public Config onMeterRemoved(Consumer<io.micrometer.core.instrument.Meter> meterRemovedListener) {
            return this;
        }

        @Override
        public Config namingConvention(NamingConvention convention) {
            delegate.config().namingConvention(convention);
            return this;
        }

        @Override
        public NamingConvention namingConvention() {
            return delegate.config().namingConvention();
        }

        @Override
        public Clock clock() {
            return clock;
        }

        @Override
        public Config pauseDetector(PauseDetector detector) {
            delegate.config().pauseDetector(detector);
            return this;
        }

        @Override
        public PauseDetector pauseDetector() {
            return delegate.config().pauseDetector();
        }
    }

    public class More extends MeterRegistry.More {

        @Override
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return delegate.more().longTaskTimer(prefix(name), merge(tags));
        }

        @Override
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> countFunction) {
            return delegate.more().counter(prefix(name), merge(tags), obj, countFunction);
        }

        @Override
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return delegate.more().counter(prefix(name), merge(tags), number);
        }

        @Override
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj,
                                       ToLongFunction<T> countFunction,
                                       ToDoubleFunction<T> totalTimeFunction,
                                       TimeUnit totalTimeFunctionUnit) {
            return delegate.more().timer(prefix(name), merge(tags), obj, countFunction, totalTimeFunction, totalTimeFunctionUnit);
        }

        @Override
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj,
                                       TimeUnit timeFunctionUnit, ToDoubleFunction<T> timeFunction) {
            return delegate.more().timeGauge(prefix(name), merge(tags), obj, timeFunctionUnit, timeFunction);
        }
    }

    /*
     * The abstract methods from MeterRegistry are never called, because all public methods are delegated.
     */

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(io.micrometer.core.instrument.Meter.Id id,
                                                               T obj,
                                                               ToDoubleFunction<T> valueFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(io.micrometer.core.instrument.Meter.Id id) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(io.micrometer.core.instrument.Meter.Id id,
                                                           DistributionStatisticConfig distributionStatisticConfig,
                                                           PauseDetector pauseDetector) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected DistributionSummary newDistributionSummary(io.micrometer.core.instrument.Meter.Id id,
                                                         DistributionStatisticConfig distributionStatisticConfig,
                                                         double scale) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected io.micrometer.core.instrument.Meter newMeter(io.micrometer.core.instrument.Meter.Id id,
                                                           io.micrometer.core.instrument.Meter.Type type,
                                                           Iterable<Measurement> measurements) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(io.micrometer.core.instrument.Meter.Id id,
                                                 T obj,
                                                 ToLongFunction<T> countFunction,
                                                 ToDoubleFunction<T> totalTimeFunction,
                                                 TimeUnit totalTimeFunctionUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(io.micrometer.core.instrument.Meter.Id id,
                                                     T obj,
                                                     ToDoubleFunction<T> countFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        throw new UnsupportedOperationException();
    }

}
