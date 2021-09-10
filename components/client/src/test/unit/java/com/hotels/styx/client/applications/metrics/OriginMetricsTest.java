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
package com.hotels.styx.client.applications.metrics;

import com.hotels.styx.api.MeterRegistry;
import com.hotels.styx.api.MicrometerRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.TimerMetric.Stopper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OriginMetricsTest {
    public static final String APP_ID = "test-id";
    public static final String ORIGIN_ID = "h1";

    private final MockClock clock;
    private final CentralisedMetrics metrics;
    private final MeterRegistry registry;

    public OriginMetricsTest() {
        clock = new MockClock();
        registry = new MicrometerRegistry(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
        metrics = new CentralisedMetrics(registry);
    }

    @Test
    public void failsIfCreatedWithNullOrigin() {
        assertThrows(NullPointerException.class,
                () -> new OriginMetrics(metrics, null));
    }

    @Test
    public void successfullyCreated() {
        assertThat(new OriginMetrics(metrics, origin(APP_ID, ORIGIN_ID)), is(notNullValue()));
    }

    @Test
    public void countsCanceledRequests() {
        OriginMetrics originMetrics = new OriginMetrics(metrics, origin(APP_ID, "h1"));

        originMetrics.requestCancelled();

        double allCount = sumCounters("proxy.client.requests.cancelled", Tags.of("originId", "h1").and("appId", APP_ID));
        assertThat(allCount, is(1.0));
    }

    @Test
    public void requestLatencyTimerTagsAppAndOrigin() {
        OriginMetrics originMetrics = new OriginMetrics(metrics, origin(APP_ID, "h1"));
        Stopper stopper = originMetrics.requestLatencyTimer().startTiming();
        clock.add(Duration.ofMillis(100));
        stopper.stop();

        Timer latencyTimer = registry.get("proxy.client.latency").tag("originId", "h1").tag("appId", APP_ID).timer();
        assertThat(latencyTimer.count(), is(1L));
        assertThat(latencyTimer.totalTime(MILLISECONDS), is(100.0));
    }

    private static Origin origin(String appId, String originId) {
        return newOriginBuilder("localhost", 8080).applicationId(appId).id(originId).build();
    }

    private double sumCounters(String name, Iterable<Tag> tags) {
        Collection<Counter> counters = registry.get(name).tags(tags).counters();
        return counters.stream().mapToDouble(Counter::count).sum();
    }
}
