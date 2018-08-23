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
package com.hotels.styx.client.applications.metrics;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.applications.OriginStats.REQUEST_FAILURE;
import static com.hotels.styx.client.applications.OriginStats.REQUEST_SUCCESS;
import static com.hotels.styx.client.netty.MetricsSupport.IsNotUpdated.hasNotReceivedUpdatesExcept;
import static com.hotels.styx.client.netty.MetricsSupport.name;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class OriginMetricsTest {
    private final Id appId;
    private final Origin origin;

    private MetricRegistry rootMetricRegistry;
    private ApplicationMetrics appMetrics;
    private OriginMetrics originMetrics;

    public static final List<String> APP_METRIC_PREFIX = singletonList("test-id");
    public static final List<String> ORIGIN_METRIC_PREFIX = asList("test-id", "h1");
    private final StubClock clock;

    public OriginMetricsTest() {
        appId = id("test-id");
        this.origin = newOriginBuilder("localhost", 1234)
                .applicationId(this.appId)
                .id("h1")
                .build();
        clock = new StubClock();
    }

    @BeforeMethod
    private void setUp() {
        rootMetricRegistry = new StubClockMeterMetricRegistry(clock);
        appMetrics = new ApplicationMetrics(appId, rootMetricRegistry);
        originMetrics = new OriginMetrics(appMetrics, origin);
    }

    @AfterMethod
    private void tearDown() {
        clearMetricsRegistry();
    }

    private void clearMetricsRegistry() {
        for (String name : rootMetricRegistry.getNames()) {
            rootMetricRegistry.deregister(name);
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsIfCreatedWithNullApplicationMetrics() {
        new OriginMetrics(null, origin);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failsIfCreatedWithNullOrigin() {
        new OriginMetrics(appMetrics, null);
    }

    @Test
    public void successfullyCreated() {
        assertThat(new OriginMetrics(appMetrics, origin), is(notNullValue()));
    }

    @Test
    public void requestSuccessIsCountedInOriginAndApplication() {
        originMetrics.requestSuccess();
        originMetrics.requestSuccess();
        originMetrics.requestSuccess();

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS));
        assertThat(meter.getCount(), is(3L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_SUCCESS));
        assertThat(meter.getCount(), is(3L));
    }

    @Test
    public void requestWithSuccessGetsAggregatedToApplication() {
        Origin originA = newOriginBuilder("hostA", 8080)
                .applicationId(this.appId)
                .id("h1")
                .build();

        Origin originB = newOriginBuilder("hostB", 8080)
                .applicationId(this.appId)
                .id("h2")
                .build();

        OriginMetrics originMetricsA = new OriginMetrics(appMetrics, originA);
        OriginMetrics originMetricsB = new OriginMetrics(appMetrics, originB);

        originMetricsA.requestSuccess();
        originMetricsA.requestSuccess();
        originMetricsB.requestSuccess();
        originMetricsB.requestSuccess();
        originMetricsB.requestSuccess();

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_SUCCESS));
        assertThat(meter.getCount(), is(5L));

        meter = rootMetricRegistry.meter(name(asList("test-id", "h1"), REQUEST_SUCCESS));
        assertThat(meter.getCount(), is(2L));

        meter = rootMetricRegistry.meter(name(asList("test-id", "h2"), REQUEST_SUCCESS));
        assertThat(meter.getCount(), is(3L));
    }

    @Test
    public void requestErrorIsCountedInOriginAndApplication() {
        originMetrics.requestError();
        originMetrics.requestError();
        originMetrics.requestError();

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE));
        assertThat(meter.getCount(), is(3L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, REQUEST_FAILURE));
        assertThat(meter.getCount(), is(3L));
    }

    @Test
    public void requestWithFailureGetsAggregatedToApplication() {
        Origin originA = newOriginBuilder("hostA", 8080)
                .applicationId(this.appId)
                .id("h1")
                .build();

        Origin originB = newOriginBuilder("hostB", 8080)
                .id("h2")
                .applicationId(this.appId)
                .build();

        OriginMetrics originMetricsA = new OriginMetrics(appMetrics, originA);
        OriginMetrics originMetricsB = new OriginMetrics(appMetrics, originB);

        originMetricsA.requestError();
        originMetricsA.requestError();
        originMetricsB.requestError();
        originMetricsB.requestError();
        originMetricsB.requestError();

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, REQUEST_FAILURE));
        assertThat(meter.getCount(), is(5L));

        meter = rootMetricRegistry.meter(name(asList("test-id", "h1"), REQUEST_FAILURE));
        assertThat(meter.getCount(), is(2L));

        meter = rootMetricRegistry.meter(name(asList("test-id", "h2"), REQUEST_FAILURE));
        assertThat(meter.getCount(), is(3L));
    }

    @Test
    public void response100ContinueUpdatesInformationalMeterOnly() {
        originMetrics.responseWithStatusCode(100);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.100"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.100"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.100"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.100"))));
    }

    @Test
    public void response101SwitchingProtocolsUpdatesInformationalMeterOnly() {
        originMetrics.responseWithStatusCode(101);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.101"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.101"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.101"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.101"))));
    }

    @Test
    public void response200OkUpdatesSuccessfulMeterOnly() {
        originMetrics.responseWithStatusCode(200);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.200"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.200"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.200"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.200"))));
    }

    @Test
    public void response204NoContentUpdatesSuccessfulMeterOnly() {
        originMetrics.responseWithStatusCode(204);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.204"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.204"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.204"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.204"))));
    }

    @Test
    public void response300MultipleChoicesUpdatesRedirectionMeterOnly() {
        originMetrics.responseWithStatusCode(300);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.300"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.300"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.300"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.300"))));
    }

    @Test
    public void response305UseProxyUpdatesRedirectionMeterOnly() {
        originMetrics.responseWithStatusCode(305);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.305"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.305"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.305"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.305"))));
    }

    @Test
    public void response400BadRequestUpdatesClientErrorOnly() {
        originMetrics.responseWithStatusCode(400);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.400"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.400"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.400"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.400"))));
    }

    @Test
    public void response403ForbiddenUpdatesClientErrorOnly() {
        originMetrics.responseWithStatusCode(403);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.403"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.403"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.403"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.403"))));
    }

    @Test
    public void response500InternalServerErrorUpdatesServerErrorOnly() {
        originMetrics.responseWithStatusCode(500);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.500"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.500"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.5xx"),
                name(APP_METRIC_PREFIX, "requests.response.status.500"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.500"))));
    }

    @Test
    public void response505HttpVersionNotSupportedUpdatesServerErrorOnly() {
        originMetrics.responseWithStatusCode(505);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.505"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.505"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.5xx"),
                name(APP_METRIC_PREFIX, "requests.response.status.505"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.505"))));
    }


    @Test
    public void collectsDifferent5xxErrorCodesUnderSeparate5xxRateMeter() {
        originMetrics.responseWithStatusCode(500);
        originMetrics.responseWithStatusCode(503);
        originMetrics.responseWithStatusCode(505);

        Meter meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.5xx"));
        assertThat(meter.getCount(), is(3L));
    }

    @Test
    public void returnsLastOneMinuteErrorRate() {
        originMetrics.responseWithStatusCode(500);
        originMetrics.responseWithStatusCode(503);
        originMetrics.responseWithStatusCode(505);
        clock.advance();

        assertThat(originMetrics.oneMinuteErrorRate(), is(0.6));
    }

    @Test
    public void invalidStatusCode600UpdatesNothing() {
        originMetrics.responseWithStatusCode(600);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.-1"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"))));
    }

    @Test
    public void invalidStatusCodeMinus100UpdatesNothing() {
        originMetrics.responseWithStatusCode(-100);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.-1"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"))));
    }

    @Test
    public void invalidStatusCode1UpdatesNothing() {
        originMetrics.responseWithStatusCode(1);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.-1"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"))));
    }

    @Test
    public void invalidStatusCode0UpdatesNothing() {
        originMetrics.responseWithStatusCode(0);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.-1"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"))));
    }

    @Test
    public void invalidStatusCode10UpdatesNothing() {
        originMetrics.responseWithStatusCode(10);

        Meter meter = rootMetricRegistry.meter(name(APP_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        meter = rootMetricRegistry.meter(name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"));
        assertThat(meter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.response.status.-1"),
                name(ORIGIN_METRIC_PREFIX, "requests.response.status.-1"))));
    }

    @Test
    public void countsCanceledRequests() throws Exception {
        originMetrics.requestCancelled();

        Counter counter = rootMetricRegistry.counter(name(APP_METRIC_PREFIX, "requests.cancelled"));
        assertThat(counter.getCount(), is(1L));

        counter = rootMetricRegistry.counter(name(ORIGIN_METRIC_PREFIX, "requests.cancelled"));
        assertThat(counter.getCount(), is(1L));

        assertThat(rootMetricRegistry, is(hasNotReceivedUpdatesExcept(
                name(APP_METRIC_PREFIX, "requests.cancelled"),
                name(ORIGIN_METRIC_PREFIX, "requests.cancelled"))));
    }

    private static class StubClock extends Clock {
        private static final int CODAHALE_METER_ADVANCE_TIME_SECONDS = 5;
        private long lastTick = 100;

        @Override
        public long getTick() {
            return lastTick;
        }

        public void advance() {
            lastTick += SECONDS.toNanos(CODAHALE_METER_ADVANCE_TIME_SECONDS + 1);
        }
    }

    private static class StubClockMeterMetricRegistry extends CodaHaleMetricRegistry {
        private final Clock clock;

        public StubClockMeterMetricRegistry(Clock clock) {
            this.clock = clock;
        }

        @Override
        public Meter meter(String name) {
            Meter metric = getMetricRegistry().getMeters().get(name);
            if (metric != null) {
                return metric;
            } else {
                try {
                    return register(name, newMeter());
                } catch (IllegalArgumentException e) {
                    Meter added = getMetricRegistry().getMeters().get(name);
                    if (added != null) {
                        return added;
                    }
                }
            }
            throw new IllegalArgumentException(name + " is already used for a different type of metric");
        }

        private Meter newMeter() {
            return new Meter(clock);
        }
    }
}
