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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonSupplierTest {
    @Test
    public void convertsObjectsToJson() {
        JsonSupplier supplier = JsonSupplier.create(() -> new Convertible("foo", 123));

        assertThat(supplier.get(), is("{\"string\":\"foo\",\"integer\":123}"));
    }

    @Test
    public void supportsModules() {
        MetricRegistry metricsRegistry = new MetricRegistry();
        metricsRegistry.register("gauge", gauge("foo"));
        metricsRegistry.counter("counter").inc(7);

        JsonSupplier supplier = JsonSupplier.create(() -> metricsRegistry, new MetricsModule(SECONDS, MILLISECONDS, false));

        assertThat(supplier.get(), is("{\"version\":\"3.1.3\",\"gauges\":{\"gauge\":{\"value\":\"foo\"}},\"counters\":{\"counter\":{\"count\":7}},\"histograms\":{},\"meters\":{},\"timers\":{}}"));
    }

    private Gauge<String> gauge(String value) {
        return () -> value;
    }

    private static class Convertible {
        private final String string;
        private final int integer;

        public Convertible(String string, int integer) {
            this.string = string;
            this.integer = integer;
        }

        @JsonProperty("string")
        public String string() {
            return string;
        }

        @JsonProperty("integer")
        public int integer() {
            return integer;
        }
    }
}