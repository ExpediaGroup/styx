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
package com.hotels.styx.metrics.reporting.graphite;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.extension.service.spi.StyxService;

import static java.lang.String.format;

/**
 * A factory that produces GraphiteReporterService.
 */
public class GraphiteReporterServiceFactory implements ServiceFactory<StyxService> {

    @Override
    public StyxService create(Environment environment, Configuration serviceConfiguration) {
        final GraphiteConfig graphiteConfig = serviceConfiguration.as(GraphiteConfig.class);
        final String host = graphiteConfig.host();
        final int port = graphiteConfig.port();

        return new GraphiteReporterService.Builder()
                .meterRegistry(environment.meterRegistry())
                .serviceName(format("Graphite-Reporter-%s:%d", host, port))
                .host(host)
                .port(port)
                .prefix(graphiteConfig.prefix())
                .reportingIntervalMillis(graphiteConfig.intervalMillis())
                .enabled(graphiteConfig.enabled())
                .tagsEnabled(graphiteConfig.tagsEnabled())
                .build();
    }
}
