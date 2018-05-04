package com.hotels.styx.metrics.reporting.graphite;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.GraphiteSender;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.Clock.defaultClock;
import static com.codahale.metrics.MetricFilter.ALL;
import static com.codahale.metrics.MetricRegistry.name;
import static com.hotels.styx.metrics.reporting.graphite.IoRetry.tryTimes;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/*
 * This file is from Coda Hale metrics project (now Yammer Metrics), and
 * modified by Expedia Inc. to add improvements in error handling, performance,
 * and some additional features.
 *
 * The file is licensed under the Apache License, Version 2.0.
 */

/**
 * A reporter which publishes metric values to a Graphite server.
 *
 * @see <a href="http://graphite.wikidot.com/">Graphite - Scalable Realtime Graphing</a>
 */
public class GraphiteReporter extends ScheduledReporter {
    /**
     * Returns a new {@link Builder} for {@link GraphiteReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link GraphiteReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    /**
     * A builder for {@link GraphiteReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.clock = defaultClock();
            this.prefix = null;
            this.rateUnit = SECONDS;
            this.durationUnit = MILLISECONDS;
            this.filter = ALL;
        }

        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds a {@link GraphiteReporter} with the given properties, sending metrics using the
         * given {@link GraphiteSender}.
         *
         * @param graphite a {@link GraphiteSender}
         * @return a {@link GraphiteReporter}
         */
        public GraphiteReporter build(GraphiteSender graphite) {
            return new GraphiteReporter(registry,
                    graphite,
                    clock,
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter);
        }
    }

    private static final Logger LOGGER = getLogger(GraphiteReporter.class);

    @VisibleForTesting
    static final int MAX_RETRIES = 5;

    private final GraphiteSender graphite;
    private final Clock clock;
    private final String prefix;

    private final LoadingCache<String, String> counterPrefixes = CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
        @Override
        public String load(String name) {
            return prefix(name, "count");
        }
    });

    private final LoadingCache<String, String> gaugePrefixes = CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
        @Override
        public String load(String name) {
            return prefix(name);
        }
    });

    private final LoadingCache<String, HistogramPrefixes> histogramPrefixes = CacheBuilder.newBuilder().build(new CacheLoader<String, HistogramPrefixes>() {
        @Override
        public HistogramPrefixes load(String name) {
            return new HistogramPrefixes(name);
        }
    });

    private final LoadingCache<String, MeteredPrefixes> meteredPrefixes = CacheBuilder.newBuilder().build(new CacheLoader<String, MeteredPrefixes>() {
        @Override
        public MeteredPrefixes load(String name) {
            return new MeteredPrefixes(name);
        }
    });

    private final LoadingCache<String, TimerPrefixes> timerPrefixes = CacheBuilder.newBuilder().build(new CacheLoader<String, TimerPrefixes>() {
        @Override
        public TimerPrefixes load(String name) {
            return new TimerPrefixes(name);
        }
    });

    private GraphiteReporter(MetricRegistry registry,
                             GraphiteSender graphite,
                             Clock clock,
                             String prefix,
                             TimeUnit rateUnit,
                             TimeUnit durationUnit,
                             MetricFilter filter) {
        super(registry, "graphite-reporter", filter, rateUnit, durationUnit);
        this.graphite = graphite;
        this.clock = clock;
        this.prefix = prefix;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        long timestamp = clock.getTime() / 1000;

        try {

            initConnection();
            gauges.forEach((name, gauge) ->
                    doReport(name, gauge, timestamp, this::reportGauge));

            counters.forEach((name, counter) ->
                    doReport(name, counter, timestamp, this::reportCounter));

            histograms.forEach((name, histogram) ->
                    doReport(name, histogram, timestamp, this::reportHistogram));

            meters.forEach((name, meter) ->
                    doReport(name, meter, timestamp, this::reportMetered));

            timers.forEach((name, timer) ->
                    doReport(name, timer, timestamp, this::reportTimer));

            graphite.flush();
        } catch (Exception e) {
            LOGGER.error("Error reporting metrics" + e.getMessage(), e);
        } finally {
            try {
                graphite.close();
            } catch (IOException e1) {
                LOGGER.warn("Error closing Graphite", graphite, e1);
            }
        }
    }

    private <M extends Metric> void doReport(String name, M metric, long timestamp, MetricReportingAction<M> consumer) {
        try {
            consumer.execute(name, metric, timestamp);
        } catch (UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error reporting metric '" + name + "': " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    void initConnection() {
        tryTimes(
                MAX_RETRIES,
                graphite::connect,
                (e) -> attemptErrorHandling(graphite::close)
        );
    }

    private void reconnectIfNecessary(Throwable e) {
        if (e instanceof IOException) {
            attemptErrorHandling(graphite::close);
            attemptErrorHandling(graphite::connect);
        } else if (e.getCause() != null) {
            reconnectIfNecessary(e.getCause());
        }
    }

    private static void attemptErrorHandling(IOAction action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error("Error during error handling: ", e);
        }
    }


    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            try {
                graphite.close();
            } catch (IOException e) {
                LOGGER.debug("Error disconnecting from Graphite", graphite, e);
            }
        }
    }

    private void reportTimer(String name, Timer timer, long timestamp) {
        final Snapshot snapshot = timer.getSnapshot();

        TimerPrefixes timerPrefixes = this.timerPrefixes.getUnchecked(name);

        doSend(timerPrefixes.max, convertDuration(snapshot.getMax()), timestamp);
        doSend(timerPrefixes.mean, convertDuration(snapshot.getMean()), timestamp);
        doSend(timerPrefixes.min, convertDuration(snapshot.getMin()), timestamp);
        doSend(timerPrefixes.stddev, convertDuration(snapshot.getStdDev()), timestamp);
        doSend(timerPrefixes.p50, convertDuration(snapshot.getMedian()), timestamp);
        doSend(timerPrefixes.p75, convertDuration(snapshot.get75thPercentile()), timestamp);
        doSend(timerPrefixes.p95, convertDuration(snapshot.get95thPercentile()), timestamp);
        doSend(timerPrefixes.p98, convertDuration(snapshot.get98thPercentile()), timestamp);
        doSend(timerPrefixes.p99, convertDuration(snapshot.get99thPercentile()), timestamp);
        doSend(timerPrefixes.p999, convertDuration(snapshot.get999thPercentile()), timestamp);

        reportMetered(name, timer, timestamp);
    }

    private void reportMetered(String name, Metered meter, long timestamp) {
        MeteredPrefixes meteredPrefixes = this.meteredPrefixes.getUnchecked(name);

        doSend(meteredPrefixes.count, meter.getCount(), timestamp);
        doSend(meteredPrefixes.m1_rate, convertRate(meter.getOneMinuteRate()), timestamp);
        doSend(meteredPrefixes.m5_rate, convertRate(meter.getFiveMinuteRate()), timestamp);
        doSend(meteredPrefixes.m15_rate, convertRate(meter.getFifteenMinuteRate()), timestamp);
        doSend(meteredPrefixes.mean_rate, convertRate(meter.getMeanRate()), timestamp);
    }

    private void reportHistogram(String name, Histogram histogram, long timestamp) {
        final Snapshot snapshot = histogram.getSnapshot();

        HistogramPrefixes histogramPrefixes = this.histogramPrefixes.getUnchecked(name);

        doSend(histogramPrefixes.count, histogram.getCount(), timestamp);
        doSend(histogramPrefixes.max, snapshot.getMax(), timestamp);
        doSend(histogramPrefixes.mean, snapshot.getMean(), timestamp);
        doSend(histogramPrefixes.min, snapshot.getMin(), timestamp);
        doSend(histogramPrefixes.stddev, snapshot.getStdDev(), timestamp);
        doSend(histogramPrefixes.p50, snapshot.getMedian(), timestamp);
        doSend(histogramPrefixes.p75, snapshot.get75thPercentile(), timestamp);
        doSend(histogramPrefixes.p95, snapshot.get95thPercentile(), timestamp);
        doSend(histogramPrefixes.p98, snapshot.get98thPercentile(), timestamp);
        doSend(histogramPrefixes.p99, snapshot.get99thPercentile(), timestamp);
        doSend(histogramPrefixes.p999, snapshot.get999thPercentile(), timestamp);
    }

    private void reportCounter(String name, Counter counter, long timestamp) {
        String counterPrefix = counterPrefixes.getUnchecked(name);

        doSend(counterPrefix, counter.getCount(), timestamp);
    }

    private void reportGauge(String name, Gauge gauge, long timestamp) {
        final String value = format(gauge.getValue());
        if (value != null) {
            String gaugePrefix = gaugePrefixes.getUnchecked(name);

            doSend(gaugePrefix, value, timestamp);
        }
    }

    private void doSend(String name, long value, long timestamp) {
        doSend(name, format(value), timestamp);
    }

    private void doSend(String name, double value, long timestamp) {
        doSend(name, format(value), timestamp);
    }


    private void doSend(String name, String value, long timestamp) {
        attemptWithRetryAndReconect(
                () -> graphite.send(name, value, timestamp));
    }

    private void attemptWithRetryAndReconect(IOAction operation) {
        tryTimes(
                MAX_RETRIES,
                operation,
                this::reconnectIfNecessary
        );
    }

    private String format(Object o) {
        if (o instanceof Float) {
            return format(((Float) o).doubleValue());
        } else if (o instanceof Double) {
            return format(((Double) o).doubleValue());
        } else if (o instanceof Byte) {
            return format(((Byte) o).longValue());
        } else if (o instanceof Short) {
            return format(((Short) o).longValue());
        } else if (o instanceof Integer) {
            return format(((Integer) o).longValue());
        } else if (o instanceof Long) {
            return format(((Long) o).longValue());
        } else if (o instanceof Boolean) {
            return format(((Boolean) o) ? 1 : 0);
        }
        return null;
    }

    private String prefix(String... components) {
        return name(prefix, components);
    }

    private String format(long n) {
        return Long.toString(n);
    }

    private String format(double v) {
        // the Carbon plaintext format is pretty underspecified, but it seems like it just wants
        // US-formatted digits
        return String.format(US, "%2.2f", v);
    }


    private interface MetricReportingAction<M> {
        void execute(String name, M metric, long timestamp);
    }

    private final class HistogramPrefixes {
        private final String count;
        private final String max;
        private final String mean;
        private final String min;
        private final String stddev;
        private final String p50;
        private final String p75;
        private final String p95;
        private final String p98;
        private final String p99;
        private final String p999;

        private HistogramPrefixes(String name) {
            this.count = prefix(name, "count");
            this.max = prefix(name, "max");
            this.mean = prefix(name, "mean");
            this.min = prefix(name, "min");
            this.stddev = prefix(name, "stddev");
            this.p50 = prefix(name, "p50");
            this.p75 = prefix(name, "p75");
            this.p95 = prefix(name, "p95");
            this.p98 = prefix(name, "p98");
            this.p99 = prefix(name, "p99");
            this.p999 = prefix(name, "p999");
        }
    }

    private final class MeteredPrefixes {
        private final String count;
        private final String m1_rate;
        private final String m5_rate;
        private final String m15_rate;
        private final String mean_rate;

        private MeteredPrefixes(String name) {
            this.count = prefix(name, "count");
            this.m1_rate = prefix(name, "m1_rate");
            this.m5_rate = prefix(name, "m5_rate");
            this.m15_rate = prefix(name, "m15_rate");
            this.mean_rate = prefix(name, "mean_rate");
        }
    }

    private final class TimerPrefixes {
        private final String max;
        private final String mean;
        private final String min;
        private final String stddev;
        private final String p50;
        private final String p75;
        private final String p95;
        private final String p98;
        private final String p99;
        private final String p999;

        private TimerPrefixes(String name) {
            this.max = prefix(name, "max");
            this.mean = prefix(name, "mean");
            this.min = prefix(name, "min");
            this.stddev = prefix(name, "stddev");
            this.p50 = prefix(name, "p50");
            this.p75 = prefix(name, "p75");
            this.p95 = prefix(name, "p95");
            this.p98 = prefix(name, "p98");
            this.p99 = prefix(name, "p99");
            this.p999 = prefix(name, "p999");
        }
    }
}
