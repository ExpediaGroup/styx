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

import com.hotels.styx.client.Connection;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.mock;

public class ConnectionPoolFactoryTest {
    private final Origin origin = newOriginBuilder("localhost", 12345).id("origin-X").applicationId("test-app").build();

    @Test
    public void registersMetricsUnderOriginsScope() {
        MetricRegistry metricRegistry = new CodaHaleMetricRegistry();
        ConnectionPoolFactory factory = new ConnectionPoolFactory.Builder()
                .connectionFactory(mock(Connection.Factory.class))
                .connectionPoolSettings(defaultConnectionPoolSettings())
                .metricRegistry(metricRegistry)
                .build();

        factory.create(origin);

        assertThat(metricRegistry.getGauges().keySet(), hasItems(
                "origins.test-app.origin-X.connectionspool.pending-connections",
                "origins.test-app.origin-X.connectionspool.available-connections",
                "origins.test-app.origin-X.connectionspool.busy-connections"
        ));
    }
}