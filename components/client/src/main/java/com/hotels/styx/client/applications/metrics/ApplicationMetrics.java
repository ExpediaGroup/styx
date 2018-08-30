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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.MetricRegistry;

import static com.codahale.metrics.MetricRegistry.name;
import static com.hotels.styx.client.applications.metrics.StatusCodes.statusCodeName;
import static java.util.Objects.requireNonNull;

/**
 * Reports metrics about applications to a {@link MetricRegistry}.
 */
public class ApplicationMetrics {
    private final MetricRegistry applicationMetrics;
    private final MetricRegistry requestScope;
    private final Timer requestLatencyTimer;
    private final Meter requestSuccessMeter;
    private final Meter requestErrorMeter;
    private final Meter status200OkMeter;
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

        this.applicationMetrics = metricRegistry.scope(appId.toString());

        this.requestScope = this.applicationMetrics.scope("requests");
        this.requestLatencyTimer = this.requestScope.timer("latency");
        this.requestSuccessMeter = this.requestScope.meter("success-rate");
        this.requestErrorMeter = this.requestScope.meter("error-rate");
        this.status200OkMeter = this.requestScope.meter(name("response", statusCodeName(200)));
        this.requestCancellations = this.requestScope.counter("cancelled");
    }

    /**
     * To be called when a request is successful.
     */
    public void requestSuccess() {
        requestSuccessMeter.mark();
    }

    /**
     * To be called when a request encounters an error.
     */
    public void requestError() {
        requestErrorMeter.mark();
    }

    /**
     * Starts request latency timer.
     *
     * @return timer context for request latency
     */
    public Timer requestLatencyTimer() {
        return requestLatencyTimer;
    }

    /**
     * Returns the metrics registry used.
     *
     * @return the metrics registry used
     */
    public MetricRegistry metricRegistry() {
        return applicationMetrics;
    }

    /**
     * Records a response with a status code.
     *
     * @param statusCode status code
     */
    public void responseWithStatusCode(int statusCode) {
        requestScope.meter(name("response", statusCodeName(statusCode))).mark();
    }

    /**
     * Records a 200 OK status.
     */
    public void responseWithStatus200Ok() {
        status200OkMeter.mark();
    }

    public void requestCancelled() {
        requestCancellations.inc();
    }
}

