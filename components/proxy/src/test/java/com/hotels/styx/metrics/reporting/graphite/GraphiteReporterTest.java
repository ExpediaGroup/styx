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
package com.hotels.styx.metrics.reporting.graphite;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.google.common.collect.ImmutableSortedMap;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.metrics.reporting.graphite.GraphiteReporter.MAX_RETRIES;
import static com.hotels.styx.metrics.reporting.graphite.GraphiteReporter.forRegistry;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

public class GraphiteReporterTest {
    private static final long TIMESTAMP = 1000198;

    private Graphite graphite;
    private GraphiteReporter reporter;
    private LoggingTestSupport logging;

    @BeforeMethod
    public void setUp() {
        Clock clock = mock(Clock.class);
        graphite = mock(Graphite.class);

        MetricRegistry registry = mock(MetricRegistry.class);

        when(clock.getTime()).thenReturn(TIMESTAMP * 1000);
        reporter = forRegistry(registry)
                .withClock(clock)
                .prefixedWith("prefix")
                .convertRatesTo(SECONDS)
                .convertDurationsTo(MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);

        logging = new LoggingTestSupport(GraphiteReporter.class);
    }

    @AfterMethod
    public void stop() {
        logging.stop();
    }

    @Test
    public void logsExceptionsDuringReportingOfAMetric() {
        reporter.report(
                map("fail_gauge", failGauge("gauge failed as intended")),
                map("fail_counter", failCounter("counter failed as intended")),
                map("fail_histogram", failHistogram("histogram failed as intended")),
                map("fail_meter", failMeter("meter failed as intended")),
                map("fail_timer", failTimer("timer failed as intended")));

        assertThat(logging.log(), hasItem(loggingEvent(ERROR, "Error reporting metric 'fail_gauge': gauge failed as intended", RuntimeException.class, "gauge failed as intended")));
        assertThat(logging.log(), hasItem(loggingEvent(ERROR, "Error reporting metric 'fail_counter': counter failed as intended", RuntimeException.class, "counter failed as intended")));
        assertThat(logging.log(), hasItem(loggingEvent(ERROR, "Error reporting metric 'fail_histogram': histogram failed as intended", RuntimeException.class, "histogram failed as intended")));
        assertThat(logging.log(), hasItem(loggingEvent(ERROR, "Error reporting metric 'fail_meter': meter failed as intended", RuntimeException.class, "meter failed as intended")));
        assertThat(logging.log(), hasItem(loggingEvent(ERROR, "Error reporting metric 'fail_timer': timer failed as intended", RuntimeException.class, "timer failed as intended")));
    }

    @Test
    public void continuesReportingMetricsAfterOneThrowsAnException() throws IOException {
        ReportCollector reported = new ReportCollector();

        doAnswer(reported::recordArguments).when(graphite).send(any(String.class), any(String.class), eq(TIMESTAMP));

        // since f < o in the alphabet, the failing metrics will try to report first

        ReportArgs reportArgs = new ReportArgs()
                .addGauge("fail_gauge", failGauge("fail"))
                .addGauge("ok_gauge", gauge(1234))

                .addCounter("fail_counter", failCounter("fail"))
                .addCounter("ok_counter", counter(2345))

                .addHistogram("fail_histogram", failHistogram("fail"))
                .addHistogram("ok_histogram", histogram(3456))

                .addMeter("fail_meter", failMeter("fail"))
                .addMeter("ok_meter", meter(4567))

                .addTimer("fail_timer", failTimer("fail"))
                .addTimer("ok_timer", timer(5678));

        reportArgs.report(reporter);

        assertThat(reported.sent(), hasItem("prefix.ok_gauge, 1234"));
        assertThat(reported.sent(), hasItem("prefix.ok_counter.count, 2345"));
        assertThat(reported.sent(), hasItem("prefix.ok_histogram.max, 3456"));
        assertThat(reported.sent(), hasItem("prefix.ok_meter.count, 4567"));
        assertThat(reported.sent(), hasItem("prefix.ok_timer.max, 5678.00"));
    }


    @Test
    public void initConnectionRetriesOnFailure() throws Exception {

        doThrow(new UnknownHostException("UNKNOWN-HOST")).when(graphite).connect();
        try {
            reporter.initConnection();
            fail("Should have thrown an exception");
        } catch (UncheckedIOException e) {
            verify(graphite, times(MAX_RETRIES)).connect();
        }
    }

    @Test
    public void failsAfterLimitOfIoExceptions() throws IOException {
        ReportCollector reported = new ReportCollector();

        doAnswer(invocationOnMock -> {
            if (invocationOnMock.getArguments()[0].equals("prefix.timer.mean")) {
                throw new IOException();
            }

            return reported.recordArguments(invocationOnMock);
        }).when(graphite).send(any(String.class), any(String.class), eq(TIMESTAMP));

        ReportArgs reportArgs = new ReportArgs()
                .addGauge("gauge", gauge(1234))
                .addCounter("counter", counter(2345))
                .addHistogram("histogram", histogram(3456))
                .addMeter("meter", meter(4567))
                .addTimer("timer", timer(5678));

        reportArgs.report(reporter);

        assertThat(reported.sent(), hasItem("prefix.meter.count, 4567"));
        assertThat(reported.sent(), not(hasItem("prefix.timer.min, 5678.00")));

        // connects when report is called, and then again after an IOException
        verify(graphite, times(MAX_RETRIES + 1)).connect();
    }

    @Test(dataProvider = "metricTypes")
    public void ioExceptionsCauseGraphiteToBeClosedAndReconnected(MetricType metricType) throws IOException {
        doThrow(IOException.class).when(graphite).send(any(String.class), any(String.class), eq(TIMESTAMP));

        Metric okMetric = metricType.create(1234);

        new ReportArgs().add(metricType, "ok_metric", okMetric).report(reporter);

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send(any(String.class), any(String.class), eq(TIMESTAMP));
        inOrder.verify(graphite).close();
        inOrder.verify(graphite).connect();
    }

    @Test
    public void doesNotReportStringGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge("value")), emptyMap(), emptyMap(), emptyMap(), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite, never()).send("prefix.gauge", "value", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsByteGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge((byte) 1)), emptyMap(), emptyMap(), emptyMap(), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.gauge", "1", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsShortGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge((short) 1)), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        verify(graphite).send("prefix.gauge", "1", TIMESTAMP);
    }

    @Test
    public void reportsIntegerGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1)), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        verify(graphite).send("prefix.gauge", "1", TIMESTAMP);
    }

    @Test
    public void reportsLongGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1L)), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        verify(graphite).send("prefix.gauge", "1", TIMESTAMP);
    }

    @Test
    public void reportsFloatGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1.1f)), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        verify(graphite).send("prefix.gauge", "1.10", TIMESTAMP);
    }

    @Test
    public void reportsDoubleGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1.1)), emptyMap(), emptyMap(), emptyMap(), emptyMap());
        verify(graphite).send("prefix.gauge", "1.10", TIMESTAMP);
    }

    @Test
    public void reportsBooleanGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(true)), emptyMap(), emptyMap(), emptyMap(), emptyMap());

        reporter.report(map("gauge", gauge(false)), emptyMap(), emptyMap(), emptyMap(), emptyMap());

        final InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.gauge", "1", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.gauge", "0", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsCounters() throws Exception {
        reporter.report(emptyMap(), map("counter", counter(100)), emptyMap(), emptyMap(), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.counter.count", "100", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsHistograms() throws Exception {
        Histogram histogram = mock(Histogram.class);
        when(histogram.getCount()).thenReturn(1L);

        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.getMax()).thenReturn(2L);
        when(snapshot.getMean()).thenReturn(3.0);
        when(snapshot.getMin()).thenReturn(4L);
        when(snapshot.getStdDev()).thenReturn(5.0);
        when(snapshot.getMedian()).thenReturn(6.0);
        when(snapshot.get75thPercentile()).thenReturn(7.0);
        when(snapshot.get95thPercentile()).thenReturn(8.0);
        when(snapshot.get98thPercentile()).thenReturn(9.0);
        when(snapshot.get99thPercentile()).thenReturn(10.0);
        when(snapshot.get999thPercentile()).thenReturn(11.0);

        when(histogram.getSnapshot()).thenReturn(snapshot);

        reporter.report(emptyMap(), emptyMap(), map("histogram", histogram), emptyMap(), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.histogram.count", "1", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.max", "2", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.mean", "3.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.min", "4", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.stddev", "5.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p50", "6.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p75", "7.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p95", "8.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p98", "9.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p99", "10.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.histogram.p999", "11.00", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsMeters() throws Exception {
        Meter meter = mock(Meter.class);
        when(meter.getCount()).thenReturn(1L);
        when(meter.getOneMinuteRate()).thenReturn(2.0);
        when(meter.getFiveMinuteRate()).thenReturn(3.0);
        when(meter.getFifteenMinuteRate()).thenReturn(4.0);
        when(meter.getMeanRate()).thenReturn(5.0);

        reporter.report(emptyMap(), emptyMap(), emptyMap(), map("meter", meter), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.meter.count", "1", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.meter.m1_rate", "2.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.meter.m5_rate", "3.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.meter.m15_rate", "4.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.meter.mean_rate", "5.00", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void reportsTimers() throws Exception {
        Timer timer = mock(Timer.class);
        when(timer.getCount()).thenReturn(1L);
        when(timer.getMeanRate()).thenReturn(2.0);
        when(timer.getOneMinuteRate()).thenReturn(3.0);
        when(timer.getFiveMinuteRate()).thenReturn(4.0);
        when(timer.getFifteenMinuteRate()).thenReturn(5.0);

        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.getMax()).thenReturn(MILLISECONDS.toNanos(100));
        when(snapshot.getMean()).thenReturn((double) MILLISECONDS.toNanos(200));
        when(snapshot.getMin()).thenReturn(MILLISECONDS.toNanos(300));
        when(snapshot.getStdDev()).thenReturn((double) MILLISECONDS.toNanos(400));
        when(snapshot.getMedian()).thenReturn((double) MILLISECONDS.toNanos(500));
        when(snapshot.get75thPercentile()).thenReturn((double) MILLISECONDS.toNanos(600));
        when(snapshot.get95thPercentile()).thenReturn((double) MILLISECONDS.toNanos(700));
        when(snapshot.get98thPercentile()).thenReturn((double) MILLISECONDS.toNanos(800));
        when(snapshot.get99thPercentile()).thenReturn((double) MILLISECONDS.toNanos(900));
        when(snapshot.get999thPercentile()).thenReturn((double) MILLISECONDS
                .toNanos(1000));

        when(timer.getSnapshot()).thenReturn(snapshot);

        reporter.report(emptyMap(), emptyMap(), emptyMap(), emptyMap(), map("timer", timer));

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).send("prefix.timer.max", "100.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.mean", "200.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.min", "300.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.stddev", "400.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p50", "500.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p75", "600.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p95", "700.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p98", "800.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p99", "900.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.p999", "1000.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.count", "1", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.m1_rate", "3.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.m5_rate", "4.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.m15_rate", "5.00", TIMESTAMP);
        inOrder.verify(graphite).send("prefix.timer.mean_rate", "2.00", TIMESTAMP);
        inOrder.verify(graphite).flush();
        inOrder.verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }

    @Test
    public void closesConnectionIfGraphiteIsUnavailable() throws Exception {
        doThrow(new UnknownHostException("UNKNOWN-HOST")).when(graphite).connect();
        reporter.report(map("gauge", gauge(1)), emptyMap(), emptyMap(), emptyMap(), emptyMap());

        InOrder inOrder = inOrder(graphite);
        inOrder.verify(graphite).connect();
        inOrder.verify(graphite).close();
    }

    @Test
    public void closesConnectionOnReporterStop() throws Exception {
        reporter.stop();

        verify(graphite).close();

        verifyNoMoreInteractions(graphite);
    }


    private static <T> SortedMap<String, T> emptyMap() {
        return ImmutableSortedMap.of();
    }

    private static <T> SortedMap<String, T> map(String name, T metric) {
        return ImmutableSortedMap.of(name, metric);
    }

    private static <T> Gauge<T> gauge(T value) {
        return () -> value;
    }

    private static Counter counter(int count) {
        Counter okCounter = new Counter();
        okCounter.inc(count);
        return okCounter;
    }

    private static Histogram histogram(int update) {
        Histogram okHistogram = new Histogram(new SlidingWindowReservoir(50));
        okHistogram.update(update);
        return okHistogram;
    }

    private static Meter meter(int mark) {
        Meter okMeter = new Meter();
        okMeter.mark(mark);
        return okMeter;
    }

    private static Timer timer(int update) {
        Timer okTimer = new Timer();
        okTimer.update(update, MILLISECONDS);
        return okTimer;
    }

    private static Gauge<Integer> failGauge(String message) {
        return () -> {
            throw new RuntimeException(message);
        };
    }

    private static Counter failCounter(String message) {
        Counter counter = mock(Counter.class);
        when(counter.getCount()).thenThrow(new RuntimeException(message));
        return counter;
    }

    private static Histogram failHistogram(String message) {
        Histogram histogram = mock(Histogram.class);
        when(histogram.getSnapshot()).thenThrow(new RuntimeException(message));
        return histogram;
    }

    private static Meter failMeter(String message) {
        Meter meter = mock(Meter.class);
        when(meter.getCount()).thenThrow(new RuntimeException(message));
        return meter;
    }

    private static Timer failTimer(String message) {
        Timer timer = mock(Timer.class);
        when(timer.getSnapshot()).thenThrow(new RuntimeException(message));
        return timer;
    }

    private static Timer ioFailTimer(String message) {
        Timer timer = mock(Timer.class);
        when(timer.getSnapshot()).thenThrow(new IOException(message));
        return timer;
    }

    @DataProvider(name = "metricTypes")
    private static Object[][] metricTypes() {
        return Stream.of(MetricType.values())
                .map(value -> new Object[]{value})
                .toArray(Object[][]::new);
    }

    private static class ReportCollector {
        private final List<String> sent = new ArrayList<>();

        Void recordArguments(InvocationOnMock invocationOnMock) {
            Object[] args = invocationOnMock.getArguments();

            sent.add(args[0] + ", " + args[1]);
            return null;
        }

        List<String> sent() {
            return sent;
        }
    }

    /*
      Greatly simplifies the code for building up maps for reporting inside the test methods.
     */
    private static class ReportArgs {
        private final SortedMap<String, Gauge> gauges = new TreeMap<>();
        private final SortedMap<String, Counter> counters = new TreeMap<>();
        private final SortedMap<String, Histogram> histograms = new TreeMap<>();
        private final SortedMap<String, Meter> meters = new TreeMap<>();
        private final SortedMap<String, Timer> timers = new TreeMap<>();

        ReportArgs addGauge(String name, Gauge gauge) {
            this.gauges.put(name, gauge);
            return this;
        }

        ReportArgs addCounter(String name, Counter counter) {
            this.counters.put(name, counter);
            return this;
        }

        ReportArgs addHistogram(String name, Histogram histogram) {
            this.histograms.put(name, histogram);
            return this;
        }

        ReportArgs addMeter(String name, Meter meter) {
            this.meters.put(name, meter);
            return this;
        }

        ReportArgs addTimer(String name, Timer timer) {
            this.timers.put(name, timer);
            return this;
        }

        public ReportArgs add(MetricType type, String name, Metric metric) {
            switch (type) {
                case GAUGE:
                    return addGauge(name, (Gauge) metric);
                case COUNTER:
                    return addCounter(name, (Counter) metric);
                case HISTOGRAM:
                    return addHistogram(name, (Histogram) metric);
                case METER:
                    return addMeter(name, (Meter) metric);
                case TIMER:
                    return addTimer(name, (Timer) metric);
                default:
                    throw new IllegalStateException();
            }
        }

        void report(GraphiteReporter reporter) {
            reporter.report(gauges, counters, histograms, meters, timers);
        }
    }

    private enum MetricType {
        GAUGE(GraphiteReporterTest::gauge),
        COUNTER(GraphiteReporterTest::counter),
        HISTOGRAM(GraphiteReporterTest::histogram),
        METER(GraphiteReporterTest::meter),
        TIMER(GraphiteReporterTest::timer),;

        private final Function<Integer, Metric> okMetricCreator;

        <M extends Metric> MetricType(Function<Integer, M> okMetricCreator) {
            this.okMetricCreator = (Function<Integer, Metric>) okMetricCreator;
        }

        public <M extends Metric> M create(int value) {
            return (M) okMetricCreator.apply(value);
        }
    }
}