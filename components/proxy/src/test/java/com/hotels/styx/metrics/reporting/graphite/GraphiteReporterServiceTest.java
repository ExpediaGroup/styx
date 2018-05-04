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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteSender;
import com.hotels.styx.common.StyxFutures;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

public class GraphiteReporterServiceTest {
    private StubGraphiteSender sender;
    private GraphiteReporterService service;
    private MetricRegistry registry;

    private LoggingTestSupport log;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport(GraphiteReporterService.class);
        sender = new StubGraphiteSender();
        registry = new MetricRegistry();
        service = new GraphiteReporterService.Builder()
                .serviceName("Graphite-Reporter-test")
                .prefix("test")
                .reportingInterval(10, MILLISECONDS)
                .graphiteSender(sender)
                .metricRegistry(registry)
                .build();
    }

    @AfterMethod
    public void stop() {
        log.stop();
    }

    @Test
    public void logsWhenServiceStarts() {
        try {
            StyxFutures.await(service.start());
            assertThat(log.lastMessage(), is(loggingEvent(INFO, "Graphite service started, service name=\"Graphite\\-Reporter\\-test\"")));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void sendsData() throws InterruptedException {
        registry.register("values", sequenceGauge(123L, 4567L, 8901L));

        for (int i = 0; i < 3; i++) {
            service.report();
        }

        assertThat(sender.data, contains("test.values=123", "test.values=4567", "test.values=8901"));
    }

    @Test
    public void formatsDoublesToTwoDecimalPlaces() throws InterruptedException {
        registry.register("values", sequenceGauge(78.99, 234.0, 123.456));

        for (int i = 0; i < 3; i++) {
            service.report();
        }

        assertThat(sender.data, contains("test.values=78.99", "test.values=234.00", "test.values=123.46"));
    }

    @Test
    public void sendsMultipleMetrics() throws InterruptedException {
        registry.register("longs", sequenceGauge(123L, 4567L));
        registry.register("ints", sequenceGauge(98, 654));

        for (int i = 0; i < 4; i++) {
            service.report();
        }

        assertThat(sender.data, containsInAnyOrder("test.longs=123", "test.ints=98", "test.longs=4567", "test.ints=654"));
    }

    @Test
    public void sendsHistogram() throws InterruptedException {
        registry.histogram("histogram").update(1234);

        service.report();

        assertThat(sender.data, hasItem(matchesRegex("test.histogram.count=1")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.max=1234")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.mean=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.min=1234")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.stddev=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p50=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p75=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p95=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p98=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p99=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.histogram.p999=1234.00")));
    }

    @Test
    public void sendsMeter() throws InterruptedException {
        registry.meter("meter").mark(1234);

        service.report();

        assertThat(sender.data, hasItem(matchesRegex("test.meter.count=1234")));
        assertThat(sender.data, hasItem(matchesRegex("test.meter.m1_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.meter.m5_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.meter.m15_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.meter.mean_rate=.*")));
    }

    @Test
    public void sendsTimer() throws InterruptedException {
        registry.timer("timer").update(1234, MILLISECONDS);

        service.report();

        assertThat(sender.data, hasItem(matchesRegex("test.timer.count=1")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.m1_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.m5_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.m15_rate=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.mean_rate=.*")));

        assertThat(sender.data, hasItem(matchesRegex("test.timer.max=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.mean=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.min=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.stddev=0.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p50=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p75=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p95=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p98=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p99=1234.00")));
        assertThat(sender.data, hasItem(matchesRegex("test.timer.p999=1234.00")));
    }

    @Test
    public void sendsCounter() throws InterruptedException {
        registry.counter("counter").inc(1234);

        service.report();

        assertThat(sender.data, hasItem(matchesRegex("test.counter.count=1234")));
    }

    private static <T extends Number> Gauge<T> sequenceGauge(T... elements) {
        Queue<T> longs = new LinkedList<>(asList(elements));

        return longs::poll;
    }

    private static class StubGraphiteSender implements GraphiteSender {
        List<String> data = new ArrayList<>();

        @Override
        public void connect() throws IllegalStateException, IOException {
        }

        @Override
        public void send(String name, String value, long timestamp) throws IOException {
            data.add(name + "=" + value);
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public int getFailures() {
            return 0;
        }

        @Override
        public void close() throws IOException {
        }
    }
}