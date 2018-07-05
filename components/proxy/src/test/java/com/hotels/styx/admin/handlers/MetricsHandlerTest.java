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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MetricsHandlerTest {
    private CodaHaleMetricRegistry metricRegistry;
    private MetricsHandler handler;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
        handler = new MetricsHandler(metricRegistry, Optional.empty());
    }

    @Test
    public void respondsToRequestWithJsonResponse() {
        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/metrics").build()));
        assertThat(response.status(), is(OK));
        assertThat(response.contentType().get(), is(JSON_UTF_8.toString()));
    }

    @Test
    public void exposesRegisteredMetrics() {
        metricRegistry.counter("foo").inc();
        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/metrics").build()));
        assertThat(response.bodyAs(UTF_8), is("{\"version\":\"3.1.3\",\"gauges\":{},\"counters\":{\"foo\":{\"count\":1}},\"histograms\":{},\"meters\":{},\"timers\":{}}"));
    }

    @Test
    public void canRequestMetricsBeginningWithPrefix() {
        metricRegistry.counter("foo.bar").inc(1);
        metricRegistry.counter("foo.bar.baz").inc(1);
        metricRegistry.counter("foo.barx").inc(1); // should not be included

        FullHttpResponse response = waitForResponse(handler.handle(get("/admin/metrics/foo.bar").build()));
        assertThat(response.bodyAs(UTF_8), is("{\"foo.bar\":{\"count\":1},\"foo.bar.baz\":{\"count\":1}}"));
    }
}
