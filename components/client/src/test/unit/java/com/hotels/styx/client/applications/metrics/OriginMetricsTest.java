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
package com.hotels.styx.client.applications.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;

import static com.hotels.styx.client.applications.metrics.OriginMetrics.APP_TAG;
import static com.hotels.styx.client.applications.metrics.OriginMetrics.ORIGIN_TAG;
import static com.hotels.styx.client.applications.metrics.OriginMetrics.CANCELLATION_COUNTER_NAME;
import static com.hotels.styx.client.applications.metrics.OriginMetrics.STATUS_CLASS_TAG;
import static com.hotels.styx.client.applications.metrics.OriginMetrics.STATUS_COUNTER_NAME;
import static com.hotels.styx.client.applications.metrics.OriginMetrics.STATUS_TAG;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OriginMetricsTest {
    public static final String APP_ID = "test-id";
    public static final String ORIGIN_ID = "h1";

    private final MockClock clock;
    private final MeterRegistry registry;

    public OriginMetricsTest() {
        clock = new MockClock();
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    }

    @Test
    public void failsIfCreatedWithNullOrigin() {
        assertThrows(NullPointerException.class,
                () -> new OriginMetrics(registry, null, APP_ID));
    }

    @Test
    public void successfullyCreated() {
        assertThat(new OriginMetrics(registry, ORIGIN_ID, APP_ID), is(notNullValue()));
    }

    @Test
    public void requestWithSuccessGetsTaggedOnApplicationAndOrigin() {
        OriginMetrics originMetricsA = new OriginMetrics(registry, "h1", APP_ID);
        OriginMetrics originMetricsB = new OriginMetrics(registry, "h2", APP_ID);

        originMetricsA.requestSuccess();
        originMetricsA.requestSuccess();
        originMetricsB.requestSuccess();
        originMetricsB.requestSuccess();
        originMetricsB.requestSuccess();

        double originACount= sumCounters(OriginMetrics.SUCCESS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1"));
        assertThat(originACount, is(2.0));

        double originBCount = sumCounters(OriginMetrics.SUCCESS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h2"));
        assertThat(originBCount, is(3.0));

        double appCount = sumCounters(OriginMetrics.SUCCESS_COUNTER_NAME, Tags.of(APP_TAG, APP_ID));
        assertThat(appCount, is(5.0));
    }

    private double sumCounters(String name, Iterable<Tag> tags) {
        Collection<Counter> counters = registry.get(name).tags(tags).counters();
        return counters.stream().mapToDouble(Counter::count).sum();
    }

    @Test
    public void requestWithFailureGetsAggregatedToApplication() {
        OriginMetrics originMetricsA = new OriginMetrics(registry, "h1", APP_ID);
        OriginMetrics originMetricsB = new OriginMetrics(registry, "h2", APP_ID);

        originMetricsA.requestError();
        originMetricsA.requestError();
        originMetricsB.requestError();
        originMetricsB.requestError();
        originMetricsB.requestError();

        double originACount= sumCounters(OriginMetrics.FAILURE_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1"));
        assertThat(originACount, is(2.0));

        double originBCount = sumCounters(OriginMetrics.FAILURE_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h2"));
        assertThat(originBCount, is(3.0));

        double appCount = sumCounters(OriginMetrics.FAILURE_COUNTER_NAME, Tags.of(APP_TAG, APP_ID));
        assertThat(appCount, is(5.0));
    }

    @Test
    public void responseWithStatusCodeTagsCounterWithCode() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);

        originMetrics.responseWithStatusCode(100);
        originMetrics.responseWithStatusCode(101);
        originMetrics.responseWithStatusCode(200);
        originMetrics.responseWithStatusCode(200);
        originMetrics.responseWithStatusCode(401);
        originMetrics.responseWithStatusCode(503);

        double allCount= sumCounters(STATUS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID));
        assertThat(allCount, is(6.0));

        double _100Count= sumCounters(STATUS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "100"));
        assertThat(_100Count, is(1.0));

        double _200Count= sumCounters(STATUS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "200"));
        assertThat(_200Count, is(2.0));
    }


    @Test
    public void responseWithStatusCodeTagsCounterWithStatusCode() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);

        originMetrics.responseWithStatusCode(500);
        originMetrics.responseWithStatusCode(503);
        originMetrics.responseWithStatusCode(505);

        double allCount= sumCounters(STATUS_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_CLASS_TAG, "5xx"));
        assertThat(allCount, is(3.0));
    }

    @Test
    public void responseWithStatusCodeTagsCounterIgnoresInvalidStatusClasses() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);

        originMetrics.responseWithStatusCode(99);
        originMetrics.responseWithStatusCode(600);
        originMetrics.responseWithStatusCode(999);
        originMetrics.responseWithStatusCode(9999);

        Counter counter99 = registry.get(STATUS_COUNTER_NAME).tags(Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "99")).counter();
        assertThat(counter99.getId().getTag(STATUS_CLASS_TAG), is(nullValue()));

        Counter counter600 = registry.get(STATUS_COUNTER_NAME).tags(Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "600")).counter();
        assertThat(counter600.getId().getTag(STATUS_CLASS_TAG), is(nullValue()));

        Counter counter999 = registry.get(STATUS_COUNTER_NAME).tags(Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "999")).counter();
        assertThat(counter999.getId().getTag(STATUS_CLASS_TAG), is(nullValue()));

        Counter counter9999 = registry.get(STATUS_COUNTER_NAME).tags(Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID).and(STATUS_TAG, "9999")).counter();
        assertThat(counter9999.getId().getTag(STATUS_CLASS_TAG), is(nullValue()));
    }


    @Test
    public void countsCanceledRequests() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);

        originMetrics.requestCancelled();

        double allCount= sumCounters(CANCELLATION_COUNTER_NAME, Tags.of(ORIGIN_TAG, "h1").and(APP_TAG, APP_ID));
        assertThat(allCount, is(1.0));
    }

    @Test
    public void requestLatencyTimerTagsAppAndOrigin() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);
        Timer.Sample timerSample = originMetrics.startTimer();
        clock.add(Duration.ofMillis(100));
        timerSample.stop(originMetrics.requestLatencyTimer());

        Timer latencyTimer = registry.get(OriginMetrics.LATENCY_TIMER_NAME).tag(ORIGIN_TAG, "h1").tag(APP_TAG, APP_ID).timer();
        assertThat(latencyTimer.count(), is(1L));
        assertThat(latencyTimer.totalTime(MILLISECONDS), is(100.0));
    }

    @Test
    public void timeToFirstByteTimerTagsAppAndOrigin() {
        OriginMetrics originMetrics = new OriginMetrics(registry, "h1", APP_ID);
        Timer.Sample timerSample = originMetrics.startTimer();
        clock.add(Duration.ofMillis(100));
        timerSample.stop(originMetrics.timeToFirstByteTimer());

        Timer latencyTimer = registry.get(OriginMetrics.TTFB_TIMER_NAME).tag(ORIGIN_TAG, "h1").tag(APP_TAG, APP_ID).timer();
        assertThat(latencyTimer.count(), is(1L));
        assertThat(latencyTimer.totalTime(MILLISECONDS), is(100.0));
    }
}
