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

import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.applications.OriginStats;
import com.hotels.styx.common.SimpleCache;
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.TimerMetric;
import io.micrometer.core.instrument.Counter;

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
    private final Counter requestSuccessMeter;
    private final Counter requestErrorMeter;
    private final Counter requestCancellations;
    private final TimerMetric requestLatency;
    private final TimerMetric timeToFirstByte;
    private final SimpleCache<Integer, Counter> responseStatus;

    /**
     * Constructor.
     *
     * @param metrics a meter registry
     * @param origin  an origin
     */
    public OriginMetrics(CentralisedMetrics metrics, Origin origin) {
        requireNonNull(origin);

        CentralisedMetrics.Proxy.Client clientMetrics = metrics.proxy().client();

        requestSuccessMeter = clientMetrics.originResponseNot5xx(origin);
        requestErrorMeter = clientMetrics.originResponse5xx(origin);
        requestCancellations = clientMetrics.requestsCancelled(origin);
        requestLatency = clientMetrics.originRequestLatency(origin);
        timeToFirstByte = clientMetrics.timeToFirstByte(origin);
        responseStatus = clientMetrics.responsesByStatus(origin);
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
        responseStatus.get(statusCode).increment();
    }

    @Override
    public void requestCancelled() {
        requestCancellations.increment();
    }

    @Override
    public TimerMetric requestLatencyTimer() {
        return requestLatency;
    }

    @Override
    public TimerMetric timeToFirstByteTimer() {
        return timeToFirstByte;
    }
}
