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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An implementation of request event sink that maintains Styx request statistics.
 */
public class RequestStatsCollector implements RequestProgressListener {
    private final Counter outstandingRequests;
    private final Timer latencyTimer;
    private final Counter[] httpResponseStatusClassCounters = new Counter[6];
    private final Map<Integer, Counter> httpServerErrorResponseCounters = new HashMap<>(6);
    private final Meter requestsIncoming;
    private final Meter responseErrorRate;

    private final Counter responsesSent;

    private final ConcurrentHashMap<Object, Long> ongoingRequests = new ConcurrentHashMap<>();

    private final NanoClock nanoClock;

    /**
     * Constructs a collector with a {@link MetricRegistry} to report stastistics to.
     *
     * @param metrics a registry to report to
     */
    public RequestStatsCollector(MetricRegistry metrics) {
        this(metrics, NanoClock.SYSTEM_CLOCK);
    }

    RequestStatsCollector(MetricRegistry metrics, NanoClock nanoClock) {
        this.nanoClock = nanoClock;
        this.outstandingRequests = metrics.counter("outstanding");
        this.latencyTimer = metrics.timer("latency");
        this.requestsIncoming = metrics.meter("received");
        this.responseErrorRate = metrics.meter("error-rate.500");
        this.responsesSent = metrics.counter("response.sent");

        httpResponseStatusClassCounters[0] = metrics.counter(responseCountName("status.unrecognised"));
        httpResponseStatusClassCounters[1] = metrics.counter(responseCountName("status.1xx"));
        httpResponseStatusClassCounters[2] = metrics.counter(responseCountName("status.2xx"));
        httpResponseStatusClassCounters[3] = metrics.counter(responseCountName("status.3xx"));
        httpResponseStatusClassCounters[4] = metrics.counter(responseCountName("status.4xx"));
        httpResponseStatusClassCounters[5] = metrics.counter(responseCountName("status.5xx"));

        for (int code : asList(500, 501, 502, 503, 504, 521)) {
            Counter counter = metrics.counter(responseCountNameForStatus(code));
            httpServerErrorResponseCounters.put(code, counter);
        }
    }

    @Override
    public void onRequest(Object requestId) {
        Long previous = this.ongoingRequests.putIfAbsent(requestId, nanoClock.nanoTime());
        if (previous == null) {
            this.outstandingRequests.inc();
            this.requestsIncoming.mark();
        }
    }

    @Override
    public void onComplete(Object requestId, int responseStatus) {
        Long startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            updateResponseStatusCounter(responseStatus);
            this.responsesSent.inc();
            this.outstandingRequests.dec();

            this.latencyTimer.update(nanoClock.nanoTime() - startTime, NANOSECONDS);
        }
    }

    @Override
    public void onTerminate(Object requestId) {
        Long startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            this.outstandingRequests.dec();

            this.latencyTimer.update(nanoClock.nanoTime() - startTime, NANOSECONDS);
        }
    }

    private void updateResponseStatusCounter(int code) {
        if (isServerError(httpStatusCodeClass(code))) {
            if (code == 500) {
                responseErrorRate.mark();
            }
            updateServerErrorCount(code);
        }

        updateStatusCodeClassCounter(httpStatusCodeClass(code));
    }

    private void updateServerErrorCount(int code) {
        Counter counter = httpServerErrorResponseCounters.get(code);
        if (counter != null) {
            counter.inc();
        }
    }

    private void updateStatusCodeClassCounter(int statusCodeClass) {
        httpResponseStatusClassCounters[statusCodeClass].inc();
    }

    private boolean isServerError(int statusCodeClass) {
        return statusCodeClass == 5;
    }

    private String responseCountName(String counterName) {
        return "response." + counterName;
    }

    private String responseCountNameForStatus(int code) {
        return responseCountName("status." + valueOf(code));
    }

    private int httpStatusCodeClass(int code) {
        if (code < 100 || code >= 600) {
            return 0;
        }

        return code / 100;
    }

    interface NanoClock {
        long nanoTime();

        NanoClock SYSTEM_CLOCK = System::nanoTime;
    }
}
