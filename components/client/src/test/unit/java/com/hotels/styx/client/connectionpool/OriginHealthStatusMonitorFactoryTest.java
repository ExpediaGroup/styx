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

import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.service.HealthCheckConfig;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitorFactory;
import com.hotels.styx.client.healthcheck.monitors.AnomalyExcludingOriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.extension.service.HealthCheckConfig.newHealthCheckConfigBuilder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class OriginHealthStatusMonitorFactoryTest {
    final Id id = GENERIC_APP;
    final OriginHealthStatusMonitorFactory factory = new OriginHealthStatusMonitorFactory();

    @Test
    public void createsNoOpMonitorForAbsentHealthCheckUri() {
        HealthCheckConfig healthCheckConfig = newHealthCheckConfigBuilder()
                .build();

        assertThat(factory.create(id, healthCheckConfig, null),
                is(instanceOf(NoOriginHealthStatusMonitor.class)));
    }

    @Test
    public void createsScheduledOriginStatusMonitor() {
        HealthCheckConfig healthCheckConfig = newHealthCheckConfigBuilder()
                .uri("/version.txt")
                .interval(5, MILLISECONDS)
                .build();

        OriginHealthCheckFunction checkFunction = (origin, callback) -> {
        };
        assertThat(factory.create(id, healthCheckConfig, () -> checkFunction),
                is(instanceOf(AnomalyExcludingOriginHealthStatusMonitor.class)));
    }

}
