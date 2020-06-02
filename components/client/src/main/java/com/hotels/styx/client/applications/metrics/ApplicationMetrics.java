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

import com.hotels.styx.api.Id;
import com.hotels.styx.api.MetricRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import static com.hotels.styx.api.MetricRegistry.name;
import static com.hotels.styx.client.applications.metrics.StatusCodes.statusCodeName;
import static java.util.Objects.requireNonNull;

/**
 * Reports metrics about applications to a {@link MetricRegistry}.
 */
public class ApplicationMetrics {

    public static final String APPID_TAG_NAME = "appid";
    public static final String REQUESTS_SCOPE = "requests";

    private final Tags appTags;

    private final MetricRegistry metricRegistry;
    private final Timer requestLatencyTimer;
    private final Timer requestTimeToFirstByteTimer;
    private final Counter requestSuccessMeter;
    private final Counter requestErrorMeter;
    private final Counter status200OkMeter;
    private final Counter requestCancellations;


    /**
     * Constructor.
     *
     * @param appId          application ID
     * @param metricRegistry metrics registry
     */
    public ApplicationMetrics(Id appId, MetricRegistry metricRegistry) {
        requireNonNull(appId);
        requireNonNull(metricRegistry);

        appTags = Tags.of(APPID_TAG_NAME, appId.toString());

        this.metricRegistry = metricRegistry;

        requestLatencyTimer = metricRegistry.timer(name(REQUESTS_SCOPE, "latency"), appTags);
        requestTimeToFirstByteTimer = metricRegistry.timer(name(REQUESTS_SCOPE, ".time-to-first-byte"), appTags);
        requestSuccessMeter = metricRegistry.counter(name(REQUESTS_SCOPE, ".success-rate"), appTags);
        requestErrorMeter = metricRegistry.counter(name(REQUESTS_SCOPE, ".error-rate"), appTags);
        status200OkMeter = metricRegistry.counter(name(REQUESTS_SCOPE, "response", statusCodeName(200)), appTags);
        requestCancellations = metricRegistry.counter(name(REQUESTS_SCOPE, "cancelled"), appTags);
    }

    /**
     * To be called when a request is successful.
     */
    public void requestSuccess() {
        requestSuccessMeter.increment();
    }

    /**
     * To be called when a request encounters an error.
     */
    public void requestError() {
        requestErrorMeter.increment();
    }

    /**
     * Returns request latency timer.
     */
    public Timer requestLatencyTimer() {
        return requestLatencyTimer;
    }

    /**
     * Returns request time-to-first-byte timer.
     */
    public Timer requestTimeToFirstByteTimer() {
        return requestTimeToFirstByteTimer;
    }

    /**
     * Returns the metrics registry used.
     *
     * @return the metrics registry used
     */
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    public Tags tags() {
        return appTags;
    }

    /**
     * Records a response with a status code.
     *
     * @param statusCode status code
     */
    public void responseWithStatusCode(int statusCode) {
        metricRegistry.counter(name(REQUESTS_SCOPE, "response", statusCodeName(statusCode)), appTags).increment();
    }

    /**
     * Records a 200 OK status.
     */
    public void responseWithStatus200Ok() {
        status200OkMeter.increment();
    }

    public void requestCancelled() {
        requestCancellations.increment();
    }
}

