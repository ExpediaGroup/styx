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
package com.hotels.styx.metrics.reporting.jmx;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.extension.service.spi.StyxService;

import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.metrics.reporting.MetricRegistryConstraints.codaHaleMetricRegistry;

/**
 * A factory that produces JmxReporterService.
 */
public class JmxReporterServiceFactory implements ServiceFactory<StyxService> {
    @Override
    public StyxService create(Environment environment, Configuration serviceConfiguration) {
        return new StyxService() {
            @Override
            public CompletableFuture<Void> start() { return CompletableFuture.completedFuture(null); }

            @Override
            public CompletableFuture<Void> stop() { return CompletableFuture.completedFuture(null); }
        };
        // String domain = serviceConfiguration.get("domain").orElse("com.hotels.styx");

        // return new JmxReporterService(domain, codaHaleMetricRegistry(environment));
    }
}
