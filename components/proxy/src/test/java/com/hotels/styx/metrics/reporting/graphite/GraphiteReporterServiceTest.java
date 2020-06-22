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

import com.hotels.styx.common.StyxFutures;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class GraphiteReporterServiceTest {
    private MeterRegistry meterRegistry;
    private GraphiteReporterService service;
    private LoggingTestSupport log;

    @BeforeEach
    public void setUp() {
        meterRegistry = new CompositeMeterRegistry();
        log = new LoggingTestSupport(GraphiteReporterService.class);
        service = new GraphiteReporterService.Builder()
                .meterRegistry(meterRegistry)
                .serviceName("Graphite-Reporter-test")
                .prefix("test")
                .host("localhost")
                .port(8080)
                .reportingIntervalMillis(10)
                .build();
    }

    @AfterEach
    public void stop() {
        log.stop();
    }

    @Test
    public void logsWhenServiceStarts() {
        try {
            StyxFutures.await(service.start());
            assertThat(log.lastMessage(), is(loggingEvent(INFO, "Graphite service started, service name=\"Graphite\\-Reporter\\-test\"")));
        } finally {
            StyxFutures.await(service.stop());
        }
    }
}
