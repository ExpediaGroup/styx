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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

public class SimpleConnectionPoolFactoryTest {
    private final Origin origin = newOriginBuilder("localhost", 12345)
            .id("origin-X")
            .applicationId("test-app")
            .build();

    @Test
    public void registersMetricsUnderOriginsScope() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        SimpleConnectionPoolFactory factory = new SimpleConnectionPoolFactory.Builder()
                .connectionFactory(mock(Connection.Factory.class))
                .connectionPoolSettings(defaultConnectionPoolSettings())
                .meterRegistry(meterRegistry)
                .build();
        factory.create(origin);

        Tags tags = Tags.of("appid", "test-app", "originid", "origin-X");
        assertThat(meterRegistry.find("connectionspool.pending-connections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("connectionspool.available-connections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("connectionspool.busy-connections").tags(tags).gauge(), notNullValue());
        assertThat(meterRegistry.find("connectionspool.connections-in-establishment").tags(tags).gauge(), notNullValue());
    }
}
