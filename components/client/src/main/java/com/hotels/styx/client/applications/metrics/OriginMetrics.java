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

import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.MeterFactory;
import com.hotels.styx.client.applications.OriginStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

/**
 * Reports metrics about origins to a {@link MetricRegistry}.
 * <p/>
 * This class is not thread-safe. It is intended to be used in a thread-confined manner.
 * Ie, always create a new instance prior using, do not pass between different threads,
 * and lose the reference when no longer needed.
 * <p/>
 * Consider twice before caching. The reference could accidentally being shared by two
 * connections scheduled on different event loops.
 */
public class OriginMetrics implements OriginStats {
    public static final String ORIGIN_TAG = "originId";
    public static final String APP_TAG = "appId";
    public static final String STATUS_TAG = "statusCode";
    public static final String STATUS_CLASS_TAG = "statusClass";

    public static final String SUCCESS_COUNTER_NAME = "request.success";
    public static final String FAILURE_COUNTER_NAME = "request.error";
    public static final String STATUS_COUNTER_NAME = "response.status";
    public static final String CANCELLATION_COUNTER_NAME = "request.cancellation";
    public static final String LATENCY_TIMER_NAME = "request.latency";
    public static final String TTFB_TIMER_NAME = "request.timeToFirstByte";

    private final MeterRegistry registry;

    private final Tags tags;

    private final Counter requestSuccessMeter;
    private final Counter requestErrorMeter;
    private final Counter requestCancellations;
    private final Timer requestLatency;
    private final Timer timeToFirstByte;

    /**
     * Constructor.
     *
     * @param registry       a meter registry
     * @param originId       an origin
     * @param appId          application ID
     */
    public OriginMetrics(MeterRegistry registry, String originId, String appId) {
        requireNonNull(originId);

        this.registry = registry;

        tags = Tags.of(ORIGIN_TAG, originId).and(APP_TAG, appId);

        requestSuccessMeter = registry.counter(SUCCESS_COUNTER_NAME, tags);
        requestErrorMeter = registry.counter(FAILURE_COUNTER_NAME, tags);
        requestCancellations = registry.counter(CANCELLATION_COUNTER_NAME, tags);
        requestLatency = MeterFactory.timer(registry, LATENCY_TIMER_NAME, tags);
        timeToFirstByte = MeterFactory.timer(registry, TTFB_TIMER_NAME, tags);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    @Override
    public void requestSuccess() {
        requestSuccessMeter.increment();
    }

    @Override
    public void requestError() {
        requestErrorMeter.increment();
    }

    @Override
    public void responseWithStatusCode(int statusCode) {
        Tags tags = this.tags.and(STATUS_TAG, valueOf(statusCode));

        if (statusCode >= 100 && statusCode < 600) {
            String statusClass = (statusCode / 100) + "xx";
            tags = tags.and(STATUS_CLASS_TAG, statusClass);
        }

        registry.counter(STATUS_COUNTER_NAME, tags).increment();
    }

    @Override
    public void requestCancelled() {
        this.requestCancellations.increment();
    }

    @Override
    public Timer requestLatencyTimer() {
        return requestLatency;
    }

    @Override
    public Timer timeToFirstByteTimer() {
        return timeToFirstByte;
    }
}
