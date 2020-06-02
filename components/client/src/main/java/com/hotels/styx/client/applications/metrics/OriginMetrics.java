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
import com.hotels.styx.client.applications.AggregateTimer;
import com.hotels.styx.client.applications.OriginStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hotels.styx.api.MetricRegistry.name;
import static com.hotels.styx.client.applications.metrics.ApplicationMetrics.REQUESTS_SCOPE;
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
    public static final String ORIGINID_TAG_NAME = "originid";

    private final ApplicationMetrics applicationMetrics;

    private final Tags originTags;

    private final Counter requestSuccessMeter;
    private final Counter requestErrorMeter;
    private final Timer requestLatency;
    private final Timer timeToFirstByte;
    private final Counter status200OkMeter;
    private final Counter errorsCatchAll;

    private final MetricRegistry registry;
    private final Counter requestCancellations;

    /**
     * Constructor.
     *
     * @param applicationMetrics application metrics
     * @param originId           an origin
     */
    public OriginMetrics(ApplicationMetrics applicationMetrics, String originId) {
        this.applicationMetrics = requireNonNull(applicationMetrics);
        requireNonNull(originId);

        originTags = Tags.concat(applicationMetrics.tags(), ORIGINID_TAG_NAME, originId);
        registry = applicationMetrics.metricRegistry();

        requestSuccessMeter = registry.counter(name(REQUESTS_SCOPE, "success-rate"), originTags);
        requestErrorMeter = registry.counter(name(REQUESTS_SCOPE, "error-rate"), originTags);
        requestLatency = registry.timer(name(REQUESTS_SCOPE, "latency"), originTags);
        timeToFirstByte = registry.timer(name(REQUESTS_SCOPE, "time-to-first-byte"), originTags);
        status200OkMeter = registry.counter(name(REQUESTS_SCOPE, "response", statusCodeName(200)), originTags);
        errorsCatchAll = registry.counter(name(REQUESTS_SCOPE, "response.status.5xx"), originTags);

        requestCancellations = registry.counter(name(REQUESTS_SCOPE, "cancelled"), originTags);
    }

    /**
     * Create a new OriginMetrics.
     *
     * @param appId          application ID
     * @param originId       an origin
     * @param metricRegistry a metrics registry
     * @return a new OriginMetrics
     */
    public static OriginMetrics create(Id appId, String originId, MetricRegistry metricRegistry) {
        ApplicationMetrics appMetrics = new ApplicationMetrics(appId, metricRegistry);
        return new OriginMetrics(appMetrics, originId);
    }

    @Override
    public void requestSuccess() {
        requestSuccessMeter.increment();
        applicationMetrics.requestSuccess();
    }

    @Override
    public void requestError() {
        requestErrorMeter.increment();
        applicationMetrics.requestError();
    }

    @Override
    public void responseWithStatusCode(int statusCode) {
        if (statusCode == 200) {
            // Optimise for common case:
            this.status200OkMeter.increment();
            this.applicationMetrics.responseWithStatus200Ok();
        } else {
            this.registry.counter(name(REQUESTS_SCOPE, "response", statusCodeName(statusCode)), originTags).increment();
            this.applicationMetrics.responseWithStatusCode(statusCode);

            if (httpStatusCodeClass(statusCode) == SERVER_ERROR_CLASS) {
                errorsCatchAll.increment();
            }
        }
    }

    @Override
    public void requestCancelled() {
        this.requestCancellations.increment();
        this.applicationMetrics.requestCancelled();
    }

    @Override
    public AggregateTimer requestLatencyTimer() {
        return new AggregateTimer(requestLatency, applicationMetrics.requestLatencyTimer());
    }

    @Override
    public AggregateTimer timeToFirstByteTimer() {
        return new AggregateTimer(timeToFirstByte, applicationMetrics.requestTimeToFirstByteTimer());
    }

    private int httpStatusCodeClass(int code) {
        if (code < 100 || code >= 600) {
            return 0;
        }

        return code / 100;
    }

}
