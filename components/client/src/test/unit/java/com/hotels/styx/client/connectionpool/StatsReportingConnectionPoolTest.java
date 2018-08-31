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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;

public class StatsReportingConnectionPoolTest {
    final Origin origin = newOriginBuilder("localhost", 9090)
            .id("backend-01")
            .build();
    final ConnectionPool delegate = new StubConnectionPool(origin);
    final MetricRegistry metricRegistry = new CodaHaleMetricRegistry();
    final StatsReportingConnectionPool pool = new StatsReportingConnectionPool(delegate, metricRegistry);

    @Test
    public void removesRegisteredMetricsOnClose() {
        assertThat(metricRegistry.getNames(), hasItems(
                "origins.generic-app.backend-01.connectionspool.available-connections",
                "origins.generic-app.backend-01.connectionspool.busy-connections",
                "origins.generic-app.backend-01.connectionspool.pending-connections",
                "origins.generic-app.backend-01.connectionspool.ttfb",
                "origins.generic-app.backend-01.connectionspool.connection-attempts",
                "origins.generic-app.backend-01.connectionspool.connection-failures",
                "origins.generic-app.backend-01.connectionspool.connections-closed",
                "origins.generic-app.backend-01.connectionspool.connections-terminated"
        ));
        pool.close();
        assertThat(metricRegistry.getNames(), is(empty()));
    }
}