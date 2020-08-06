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
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.graphite.GraphiteDimensionalNamingConvention;
import io.micrometer.graphite.GraphiteHierarchicalNamingConvention;
import io.micrometer.graphite.GraphiteMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class GraphiteReporterServiceTest {
    private CompositeMeterRegistry meterRegistry;
    private LoggingTestSupport log;

    @BeforeEach
    public void setUp() {
        meterRegistry = new CompositeMeterRegistry();
        log = new LoggingTestSupport(GraphiteReporterService.class);
    }

    @AfterEach
    public void stop() {
        log.stop();
    }

    GraphiteReporterService.Builder serviceBuilder() {
        return new GraphiteReporterService.Builder()
                .meterRegistry(meterRegistry)
                .serviceName("Graphite-Reporter-test")
                .host("localhost")
                .port(8080)
                .reportingIntervalMillis(10);
    }

    GraphiteMeterRegistry graphiteRegistry() {
        return (GraphiteMeterRegistry) meterRegistry.getRegistries().iterator().next();
    }

    @Test
    public void logsWhenServiceStarts() {
        GraphiteReporterService service = serviceBuilder().build();

        try {
            StyxFutures.await(service.start());
            assertThat(log.lastMessage(), is(loggingEvent(INFO, "Graphite service started, service name=\"Graphite\\-Reporter\\-test\"")));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void supportsGraphiteWithTags() {
        GraphiteReporterService service = serviceBuilder().tagsEnabled(true).build();

        try {
            StyxFutures.await(service.start());
            assertThat(graphiteRegistry().config().namingConvention(), instanceOf(GraphiteDimensionalNamingConvention.class));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void supportsGraphiteWithoutTags() {
        GraphiteReporterService service = serviceBuilder().tagsEnabled(false).build();

        try {
            StyxFutures.await(service.start());
            assertThat(graphiteRegistry().config().namingConvention(), instanceOf(GraphiteHierarchicalNamingConvention.class));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void defaultsToNoTagSupport() {
        GraphiteReporterService service = serviceBuilder().build();

        try {
            StyxFutures.await(service.start());
            assertThat(graphiteRegistry().config().namingConvention(), instanceOf(GraphiteHierarchicalNamingConvention.class));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void appliesPrefixToHierarchicalMetricNames() {
        GraphiteReporterService service = serviceBuilder().prefix("theprefix").build();

        try {
            StyxFutures.await(service.start());
            meterRegistry.counter("mycounter").increment();
            meterRegistry.counter("taggedCounter", "mytag", "myvalue").increment();
            assertThat(graphiteRegistry().getDropwizardRegistry().meter("theprefix.mycounter").getCount(), equalTo(1L));
            assertThat(graphiteRegistry().getDropwizardRegistry().meter("theprefix.taggedCounter.mytag.myvalue").getCount(), equalTo(1L));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void appliesPrefixToDimensionalMetricNames() {
        GraphiteReporterService service = serviceBuilder().prefix("theprefix").tagsEnabled(true).build();

        try {
            StyxFutures.await(service.start());
            meterRegistry.counter("mycounter").increment();
            meterRegistry.counter("taggedCounter", "mytag", "myvalue").increment();
            assertThat(graphiteRegistry().getDropwizardRegistry().meter("theprefix.mycounter").getCount(), equalTo(1L));
            assertThat(graphiteRegistry().getDropwizardRegistry().meter("theprefix.taggedCounter;mytag=myvalue").getCount(), equalTo(1L));
        } finally {
            StyxFutures.await(service.stop());
        }
    }

    @Test
    public void appliesNoPrefixByDefaultToMetricNames() {
        GraphiteReporterService service = serviceBuilder().build();

        try {
            StyxFutures.await(service.start());
            meterRegistry.counter("mycounter").increment();
            assertThat(graphiteRegistry().getDropwizardRegistry().meter("mycounter").getCount(), equalTo(1L));
        } finally {
            StyxFutures.await(service.stop());
        }
    }
}
