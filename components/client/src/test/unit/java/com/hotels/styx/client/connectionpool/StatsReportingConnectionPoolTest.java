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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.MeterRegistry;
import com.hotels.styx.api.MicrometerRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.Metrics.APPID_TAG;
import static com.hotels.styx.api.Metrics.ORIGINID_TAG;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class StatsReportingConnectionPoolTest {
    final Origin origin = newOriginBuilder("localhost", 9090)
            .id("backend-01")
            .build();

    final MeterRegistry meterRegistry = new MicrometerRegistry(new SimpleMeterRegistry());

    final ConnectionPool delegate = new StubConnectionPool(origin);
    final StatsReportingConnectionPool pool = new StatsReportingConnectionPool(delegate, new CentralisedMetrics(meterRegistry));

    @Test
    public void removesRegisteredMetricsOnClose() {
        Tags tags = Tags.of(APPID_TAG, "generic-app", ORIGINID_TAG, "backend-01");

        assertThat(meterRegistry.find("proxy.client.connectionpool.availableConnections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.busyConnections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.pendingConnections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionAttempts").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsInEstablishment").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionFailures").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsClosed").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsTerminated").tags(tags).gauge(), notNullValue());

        pool.close();

        assertThat(meterRegistry.find("proxy.client.connectionpool.availableConnections").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.busyConnections").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.pendingConnections").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionAttempts").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsInEstablishment").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionFailures").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsClosed").tags(tags).gauge(), nullValue());
        assertThat(meterRegistry.find("proxy.client.connectionpool.connectionsTerminated").tags(tags).gauge(), nullValue());

    }
}
