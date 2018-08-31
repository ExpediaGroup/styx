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
package com.hotels.styx.admin.dashboard;

import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.COUNTER;
import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.METER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ResponseCodeSupplierTest {
    private MetricRegistry metricRegistry;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
    }

    @Test
    public void gathersStatusCodesFromCounters() {
        ResponseCodeSupplier responseCodeSupplier = new ResponseCodeSupplier(metricRegistry, COUNTER, "foo.bar", true);

        metricRegistry.counter("foo.bar.200").inc(1234);
        metricRegistry.counter("foo.bar.500").inc(111);
        metricRegistry.counter("foo.bar.502").inc(222);

        Map<String, Integer> result = responseCodeSupplier.get();

        assertThat(result.get("200"), is(1234));
        assertThat(result.get("500"), is(111));
        assertThat(result.get("502"), is(222));

        assertThat(result.get("2xx"), is(1234));
        assertThat(result.get("5xx"), is(333));
    }

    @Test
    public void gathersStatusCodesFromMeters() {
        ResponseCodeSupplier responseCodeSupplier = new ResponseCodeSupplier(metricRegistry, METER, "foo.bar", true);

        metricRegistry.meter("foo.bar.200").mark(1234);
        metricRegistry.meter("foo.bar.500").mark(111);
        metricRegistry.meter("foo.bar.502").mark(222);

        Map<String, Integer> result = responseCodeSupplier.get();

        assertThat(result.get("200"), is(1234));
        assertThat(result.get("500"), is(111));
        assertThat(result.get("502"), is(222));

        assertThat(result.get("2xx"), is(1234));
        assertThat(result.get("5xx"), is(333));
    }

    @Test
    public void canExcludeNonErrorCodesFromAggregation() {
        ResponseCodeSupplier responseCodeSupplier = new ResponseCodeSupplier(metricRegistry, COUNTER, "foo.bar", false);

        metricRegistry.counter("foo.bar.200").inc(1234);
        metricRegistry.counter("foo.bar.500").inc(111);

        Map<String, Integer> result = responseCodeSupplier.get();

        assertThat(result.get("2xx"), is(nullValue()));
    }

    @Test
    public void maintainsDataBetweenRequests() {
        ResponseCodeSupplier responseCodeSupplier = new ResponseCodeSupplier(metricRegistry, COUNTER, "foo.bar", true);

        metricRegistry.counter("foo.bar.200").inc(1234);

        Map<String, Integer> result = responseCodeSupplier.get();

        assertThat(result.get("200"), is(1234));
        assertThat(result.get("2xx"), is(1234));

        result = responseCodeSupplier.get();

        assertThat(result.get("200"), is(1234));
        assertThat(result.get("2xx"), is(1234));
    }

    @Test
    public void ignoresNonNumberMetrics() {
        ResponseCodeSupplier responseCodeSupplier = new ResponseCodeSupplier(metricRegistry, COUNTER, "foo.bar", true);

        metricRegistry.counter("foo.bar.200").inc(1234);
        metricRegistry.counter("foo.bar.500").inc(111);
        metricRegistry.counter("foo.bar.502").inc(222);
        metricRegistry.counter("foo.bar.5xx").inc(676);
        metricRegistry.counter("foo.bar.foo").inc(61515);

        Map<String, Integer> result = responseCodeSupplier.get();

        assertThat(result.get("200"), is(1234));
        assertThat(result.get("500"), is(111));
        assertThat(result.get("502"), is(222));

        assertThat(result.get("2xx"), is(1234));
        assertThat(result.get("5xx"), is(333));
    }
}
