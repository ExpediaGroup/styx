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
package com.hotels.styx;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.eventbus.AsyncEventBus;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;

import static com.hotels.styx.Version.readVersionFrom;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

final class Environments {
    private Environments() {
    }

    static Environment newEnvironment(StyxConfig styxConfig) {
        return new Environment.Builder()
                .configuration(styxConfig)
                .metricsRegistry(new CodaHaleMetricRegistry())
                .healthChecksRegistry(new HealthCheckRegistry())
                .buildInfo(readBuildInfo())
                .eventBus(new AsyncEventBus("styx", newSingleThreadExecutor()))
                .build();
    }

    private static Version readBuildInfo() {
        return readVersionFrom("/version.json");
    }
}
