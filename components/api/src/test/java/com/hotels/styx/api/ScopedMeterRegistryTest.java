/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScopedMeterRegistryTest {
    private MeterRegistry baseRegistry;
    private ScopedMeterRegistry scopedRegistry;

    @BeforeEach
    public void createRegistries() {
        baseRegistry = new MicrometerRegistry(new SimpleMeterRegistry());
        scopedRegistry = baseRegistry.scope("testscope");
    }

    @Test
    public void createsScopedCounters() {
        scopedRegistry.counter("counter2", "tagkey", "tagvalue").increment(2.0);
        scopedRegistry.counter("counter3", tag("tagkey", "tagvalue")).increment();

        assertThat(baseRegistry.counter("testscope.counter2", "tagkey", "tagvalue").count(), is(2.0));
        assertThat(baseRegistry.counter("testscope.counter3", "tagkey", "tagvalue").count(), is(1.0));
    }

    @Test
    public void createsScopedSummaries() {
        scopedRegistry.summary("summary1", "tagkey", "tagvalue").record(3.0);
        scopedRegistry.summary("summary2", tag("tagkey", "tagvalue")).record(1.0);

        assertThat(baseRegistry.summary("testscope.summary1", "tagkey", "tagvalue").totalAmount(), is(3.0));
        assertThat(baseRegistry.summary("testscope.summary2", tag("tagkey", "tagvalue")).totalAmount(), is(1.0));
    }

    @Test
    public void canRetrieveScopedMetrics() {
        baseRegistry.counter("testscope.metric").increment();

        assertThat(scopedRegistry.get("metric").counter().count(), is(1.0));
        assertThat(scopedRegistry.find("metric").counter().count(), is(1.0));
    }

    @Test
    public void canIterateMetrics() {
        // Using getMeters

        scopedRegistry.counter("counter").increment(2.0);
        scopedRegistry.summary("summary").record(1.0);

        List<Meter> meters = scopedRegistry.getMeters();

        assertThat(meters.size(), is(2));
        assertThat(meters.stream().filter(meter -> meter instanceof Counter).count(), is(1L));
        assertThat(meters.stream().filter(meter -> meter instanceof DistributionSummary).count(), is(1L));

        // Using forEachMeter

        AtomicInteger total = new AtomicInteger();
        AtomicInteger counters = new AtomicInteger();
        AtomicInteger summaries = new AtomicInteger();

        scopedRegistry.forEachMeter(meter -> {
            total.incrementAndGet();
            if (meter instanceof Counter) {
                counters.incrementAndGet();
            } else if (meter instanceof DistributionSummary) {
                summaries.incrementAndGet();
            }
        });

        assertThat(total.get(), is(2));
        assertThat(counters.get(), is(1));
        assertThat(summaries.get(), is(1));
    }

    @Test
    public void hasConfig() {
        assertThat(scopedRegistry.config(), is(baseRegistry.config()));
    }

    @Test
    public void createsScopedTimers() {
        scopedRegistry.timer("timer1", "tagkey", "tagvalue").record(3L, SECONDS);
        scopedRegistry.timer("timer2", tag("tagkey", "tagvalue")).record(2L, SECONDS);

        assertThat(baseRegistry.timer("testscope.timer1", "tagkey", "tagvalue").totalTime(SECONDS), is(3.0));
        assertThat(baseRegistry.timer("testscope.timer2", tag("tagkey", "tagvalue")).totalTime(SECONDS), is(2.0));
    }

    @Test
    public void createsScopedGauges() {
        scopedRegistry.gauge("gauge1", 123);
        scopedRegistry.gauge("gauge2", tag("t2k", "t2v"), 234);
        scopedRegistry.gauge("gauge3", "345", Integer::parseInt);
        scopedRegistry.gauge("gauge4", tag("t4k", "t4v"), "456", Integer::parseInt);
        scopedRegistry.gaugeCollectionSize("gauge5", tag("t5k", "t5v"), asList("a", "b", "c"));
        scopedRegistry.gaugeMapSize("gauge6", tag("t6k", "t6v"), Map.of("a", "1", "b", "2"));

        assertThat(gaugeValue("testscope.gauge1"), is(123.0));
        assertThat(gaugeValue("testscope.gauge2"), is(234.0));
        assertThat(gaugeValue("testscope.gauge3"), is(345.0));
        assertThat(gaugeValue("testscope.gauge4"), is(456.0));
        assertThat(gaugeValue("testscope.gauge5"), is(3.0));
        assertThat(gaugeValue("testscope.gauge6"), is(2.0));
    }

    @Test
    public void createsMoreScopedMetrics() {
        MeterRegistry.More more = scopedRegistry.more();

        more.longTaskTimer("ltt_1", "tk", "tv").record(() -> {
        });

        more.longTaskTimer("ltt_2", tag("tk", "tv")).record(() -> {
        });

        more.counter("fc1", tag("tk", "tv"), 123.0);
        more.counter("fc2", tag("tk", "tv"), "234", Integer::parseInt);

        more.timer("timer1", tag("tk", "tv"), new Pair<>(2, 345), (pair) -> pair.getFirst(), (pair) -> pair.getSecond(), SECONDS);

        more.timeGauge("timeGauge1", tag("tk", "tv"), "456", SECONDS, Double::parseDouble);

        assertThat(baseRegistry.find("testscope.ltt_1").tags(tag("tk", "tv")).longTaskTimer(), is(notNullValue()));
        assertThat(baseRegistry.find("testscope.ltt_2").tags(tag("tk", "tv")).longTaskTimer(), is(notNullValue()));

        assertThat(baseRegistry.find("testscope.fc1").tags(tag("tk", "tv")).functionCounter(), is(notNullValue()));
        assertThat(baseRegistry.find("testscope.fc2").tags(tag("tk", "tv")).functionCounter(), is(notNullValue()));

        assertThat(baseRegistry.find("testscope.timer1").tags("tk", "tv").functionTimer().count(), is(2.0));
        assertThat(baseRegistry.find("testscope.timer1").tags("tk", "tv").functionTimer().totalTime(SECONDS), is(345.0));

        assertThat(baseRegistry.find("testscope.timeGauge1").tag("tk", "tv").timeGauge().value(SECONDS), is(456.0));
    }

    @Test
    public void removesMeters() {
        Counter counter = scopedRegistry.counter("meter1");
        assertThat(baseRegistry.find("testscope.meter1").counter(), is(notNullValue()));
        scopedRegistry.remove(counter);
        assertThat(baseRegistry.find("testscope.meter1").counter(), is(nullValue()));

        Counter counter2 = scopedRegistry.counter("meter2");
        assertThat(baseRegistry.find("testscope.meter2").counter(), is(notNullValue()));
        scopedRegistry.remove(counter2.getId().withName("meter2"));
        assertThat(baseRegistry.find("testscope.meter2").counter(), is(nullValue()));
    }

    @Test
    public void clearsMeters() {
        scopedRegistry.counter("meter1");
        assertThat(baseRegistry.getMeters(), is(not(emptyIterable())));
        scopedRegistry.clear();
        assertThat(baseRegistry.getMeters(), is(emptyIterable()));
    }

    @Test
    public void closes() {
        assertFalse(scopedRegistry.isClosed());
        assertFalse(baseRegistry.isClosed());

        scopedRegistry.close();

        assertTrue(scopedRegistry.isClosed());
        assertTrue(baseRegistry.isClosed());
    }

    private double gaugeValue(String name, String... tags) {
        Gauge gauge = baseRegistry.find(name).tags(tags).gauge();

        assertThat(gauge, is(notNullValue()));

        return gauge.value();
    }

    private static Iterable<Tag> tag(String name, String value) {
        return List.of(Tag.of(name, value));
    }
}
