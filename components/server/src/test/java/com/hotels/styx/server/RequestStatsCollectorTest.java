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
package com.hotels.styx.server;

import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.hotels.styx.api.HttpRequest.get;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

public class RequestStatsCollectorTest {
    CodaHaleMetricRegistry metrics;
    Object requestId = get("/requestId1").build().id();
    Object requestId2 = get("/requestId2").build().id();
    TestClock clock = new TestClock();
    RequestStatsCollector sink;

    @BeforeMethod
    public void setUp() {
        metrics = new CodaHaleMetricRegistry();
        clock.setNanoTime(0);
        sink = new RequestStatsCollector(metrics, clock);
    }

    @Test
    public void maintainsOutstandingRequestsCount() {
        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onComplete(requestId, 200);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
    }

    @Test
    public void ignoresAdditionalCallsToOnRequestWithSameRequestId() {
        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));
    }

    @Test
    public void maintainsOutstandingRequestsCountForSeveralSimultaneousRequests() {
        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onRequest(requestId2);
        assertThat(metrics.counter("outstanding").getCount(), is(2L));

        sink.onComplete(requestId, 200);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onTerminate(requestId2);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
    }

    @Test
    public void doesNotDecrementOutstandingRequestForUnknownRequestIds() {
        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onComplete(requestId2, 200);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onTerminate(requestId2);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onComplete(requestId, 200);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
    }

    @Test
    public void decrementsOutstandingRequestCountWithOnTerminated() {
        sink.onRequest(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        sink.onTerminate(requestId);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
    }

    @Test
    public void maintainsRequestLatencyTimer() throws InterruptedException {
        sink.onRequest(requestId);
        clock.setNanoTime(100, MILLISECONDS);
        sink.onComplete(requestId, 200);

        assertThat(metrics.timer("latency").getCount(), is(1L));
        assertThat(metrics.timer("latency").getSnapshot().getMean(), is(closeTo(MILLISECONDS.toNanos(100), MILLISECONDS.toNanos(2))));
    }

    @Test
    public void maintainsRequestLatencyTimerForMultipleOngoingRequests() throws InterruptedException {
        sink.onRequest(requestId);
        sink.onRequest(requestId2);

        clock.setNanoTime(100, MILLISECONDS);

        sink.onComplete(requestId, 200);
        assertThat(metrics.timer("latency").getCount(), is(1L));
        assertThat(metrics.timer("latency").getSnapshot().getMean(), is(closeTo(MILLISECONDS.toNanos(100), MILLISECONDS.toNanos(2))));

        clock.setNanoTime(200, MILLISECONDS);

        sink.onTerminate(requestId2);
        assertThat(metrics.timer("latency").getCount(), is(2L));
        assertThat(metrics.timer("latency").getSnapshot().getMean(), is(closeTo(MILLISECONDS.toNanos(150), MILLISECONDS.toNanos(2))));
    }

    @Test
    public void stopsLatencyTimerWhenConnectionResets() throws InterruptedException {
        sink.onRequest(requestId);
        clock.setNanoTime(100, MILLISECONDS);
        sink.onTerminate(requestId);

        assertThat(metrics.timer("latency").getCount(), is(1L));
        assertThat(metrics.timer("latency").getSnapshot().getMean(), is(closeTo(MILLISECONDS.toNanos(100), MILLISECONDS.toNanos(2))));
    }

    @Test
    public void maintainsIncomingRequestRate() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 200);
        sink.onRequest(requestId);
        sink.onComplete(requestId, 200);

        assertThat(metrics.meter("received").getCount(), is(2L));
    }

    @Test
    public void reports200ResponsesAs2xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 200);
        assertThat(metrics.counter("response.status.2xx").getCount(), is(1L));
    }

    @Test
    public void reports201ResponsesAs2xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 201);
        assertThat(metrics.counter("response.status.2xx").getCount(), is(1L));
    }

    @Test
    public void reports204ResponsesAs2xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 204);
        assertThat(metrics.counter("response.status.2xx").getCount(), is(1L));
    }

    @Test
    public void reports400ResponsesAs4xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 400);
        assertThat(metrics.counter("response.status.4xx").getCount(), is(1L));
    }

    @Test
    public void reports404ResponsesAs4xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 404);
        assertThat(metrics.counter("response.status.4xx").getCount(), is(1L));
    }

    @Test
    public void reports500Responses() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 500);
        assertThat(metrics.counter("response.status.500").getCount(), is(1L));
        assertThat(metrics.counter("response.status.5xx").getCount(), is(1L));
    }

    @Test
    public void reports504Responses() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 504);
        assertThat(metrics.counter("response.status.504").getCount(), is(1L));
        assertThat(metrics.counter("response.status.5xx").getCount(), is(1L));
    }

    @Test
    public void reportsUnknownServerErrorCodesAs5xx() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 566);
        assertThat(metrics.counter("response.status.5xx").getCount(), is(1L));
    }

    @Test
    public void reportsUnrecognisedHttpSatusCodesLessThan100() throws Exception {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 99);
        assertThat(metrics.counter("response.status.unrecognised").getCount(), is(1L));
    }

    @Test
    public void reportsUnrecognisedHttpSatusCodesGreaterThan599() throws Exception {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 600);
        assertThat(metrics.counter("response.status.unrecognised").getCount(), is(1L));
    }

    @Test
    public void maintainsResponsesSentCount() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 200);
        assertThat(metrics.counter("response.sent").getCount(), is(1L));
    }

    @Test
    public void shouldRecord500Errors() {
        sink.onRequest(requestId);
        sink.onComplete(requestId, 500);

        sink.onRequest(requestId2);
        sink.onComplete(requestId2, 501);

        assertThat(metrics.meter("error-rate.500").getCount(), is(1L));
    }

    private static final class TestClock implements RequestStatsCollector.NanoClock {
        private long nanoTime;

        @Override
        public long nanoTime() {
            return nanoTime;
        }

        public void setNanoTime(long nanoTime) {
            this.nanoTime = nanoTime;
        }

        public void setNanoTime(long time, TimeUnit timeUnit) {
            this.nanoTime = timeUnit.toNanos(time);
        }
    }
}