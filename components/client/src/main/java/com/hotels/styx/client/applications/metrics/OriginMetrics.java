/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import com.hotels.styx.client.applications.AggregateTimer;
import com.hotels.styx.client.applications.OriginStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;
import static com.hotels.styx.client.applications.metrics.StatusCodes.statusCodeName;
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
    private static final Logger LOG = LoggerFactory.getLogger(OriginMetrics.class);
    private static final int SERVER_ERROR_CLASS = 5;

    private final ApplicationMetrics applicationMetrics;

    private final String requestMetricPrefix;

    private final Meter requestSuccessMeter;
    private final Meter requestErrorMeter;
    private final Timer requestLatency;
    private final Meter status200OkMeter;
    private final Meter errorsCatchAll;

    private final MetricRegistry registry;
    private final Counter requestCancellations;

    /**
     * Constructor.
     *
     * @param applicationMetrics application metrics
     * @param origin             an origin
     */
    public OriginMetrics(ApplicationMetrics applicationMetrics, String originId) {
        this.applicationMetrics = requireNonNull(applicationMetrics);
        requireNonNull(originId);

        this.registry = this.applicationMetrics.metricRegistry();
        this.requestMetricPrefix = name(originId, "requests");

        this.requestSuccessMeter = this.registry.meter(name(this.requestMetricPrefix, "success-rate"));
        this.requestErrorMeter = this.registry.meter(name(this.requestMetricPrefix, "error-rate"));
        this.requestLatency = this.registry.timer(name(this.requestMetricPrefix, "latency"));
        this.status200OkMeter = this.registry.meter(name(this.requestMetricPrefix, "response", statusCodeName(200)));
        this.errorsCatchAll = this.registry.meter(name(this.requestMetricPrefix, "response.status.5xx"));

        this.requestCancellations = this.registry.counter(name(this.requestMetricPrefix, "cancelled"));
    }

    /**
     * Create a new OriginMetrics.
     *
     * @param origin         an origin
     * @param metricRegistry a metrics registry
     * @return a new OriginMetrics
     */
    public static OriginMetrics create(Id appId, String originId, MetricRegistry metricRegistry) {
        ApplicationMetrics appMetrics = new ApplicationMetrics(appId, metricRegistry);
        return new OriginMetrics(appMetrics, originId);
    }

    @Override
    public void requestSuccess() {
        requestSuccessMeter.mark();
        applicationMetrics.requestSuccess();
    }

    @Override
    public void requestError() {
        requestErrorMeter.mark();
        applicationMetrics.requestError();
    }

    @Override
    public void responseWithStatusCode(int statusCode) {
        if (statusCode == 200) {
            // Optimise for common case:
            this.status200OkMeter.mark();
            this.applicationMetrics.responseWithStatus200Ok();
        } else {
            this.registry.meter(name(this.requestMetricPrefix, "response", statusCodeName(statusCode))).mark();
            this.applicationMetrics.responseWithStatusCode(statusCode);

            if (httpStatusCodeClass(statusCode) == SERVER_ERROR_CLASS) {
                errorsCatchAll.mark();
            }
        }
    }

    @Override
    public double oneMinuteErrorRate() {
        return errorsCatchAll.getOneMinuteRate();
    }

    @Override
    public void requestCancelled() {
        this.requestCancellations.inc();
        this.applicationMetrics.requestCancelled();
    }

    @Override
    public AggregateTimer requestLatencyTimer() {
        return new AggregateTimer(requestLatency, applicationMetrics.requestLatencyTimer());
    }

    private int httpStatusCodeClass(int code) {
        if (code < 100 || code >= 600) {
            return 0;
        }

        return code / 100;
    }

}
