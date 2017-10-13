/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.metrics.reporting.jmx;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.AbstractIdleService;

/**
 * Builds JMX reporter from configuration and wraps it in service interface.
 */
public class JmxReporterService extends AbstractIdleService {

    private final JmxReporter reporter;

    public JmxReporterService(String domain, MetricRegistry metricRegistry) {
        this.reporter = JmxReporter.forRegistry(metricRegistry)
                .inDomain(domain)
                .build();
    }

    @Override
    protected void startUp() {
        reporter.start();
    }

    @Override
    protected void shutDown() {
        reporter.stop();
    }
}
