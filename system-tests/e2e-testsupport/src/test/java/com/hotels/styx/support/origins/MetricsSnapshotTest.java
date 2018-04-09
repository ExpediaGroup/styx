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
package com.hotels.styx.support.origins;

import com.codahale.metrics.json.MetricsModule;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.admin.dashboard.JsonSupplier;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import com.hotels.styx.utils.MetricsSnapshot;

import java.util.Optional;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class MetricsSnapshotTest {

    private CodaHaleMetricRegistry registry;
    private Supplier<String> jsonSupplier;

    @BeforeMethod
    public void setUp() throws Exception {
        registry = new CodaHaleMetricRegistry();
        jsonSupplier = JsonSupplier.create(() -> registry, new MetricsModule(SECONDS, MILLISECONDS, false));
    }

    @Test
    public void returnsMetricsIfAvailable() throws Exception {
        registry.meter("my.meter").mark();

        Optional<ImmutableMap<String, Object>> meter = MetricsSnapshot.fromString(jsonSupplier.get())
                .getMetric("meters", "my.meter");

        assertThat(meter.isPresent(), is(true));
    }

    @Test
    public void returnsAbsentIfMetricIsNotAvailable() throws Exception {
        Optional<ImmutableMap<String, Object>> meter = MetricsSnapshot.fromString(jsonSupplier.get())
                .getMetric("meters", "my.meter");

        assertThat(meter.isPresent(), is(false));
    }

    @Test
    public void returnsAbsentIfWrongMetricsType() throws Exception {
        registry.meter("my.meter").mark();

        Optional<ImmutableMap<String, Object>> meter = MetricsSnapshot.fromString(jsonSupplier.get())
                .getMetric("wrong type", "my.meter");

        assertThat(meter.isPresent(), is(false));
    }


}