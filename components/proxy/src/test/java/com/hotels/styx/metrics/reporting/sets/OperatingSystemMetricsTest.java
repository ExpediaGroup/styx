/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.metrics.reporting.sets;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class OperatingSystemMetricsTest {

    @Test
    public void unixOnlyMetricsAreCreated() {
        MeterRegistry registry = new SimpleMeterRegistry();

        new OperatingSystemMetrics().bindTo(registry);
        assertThat(registry.find("os.fileDescriptors.open").gauge(), notNullValue());
        assertThat(registry.find("os.fileDescriptors.max").gauge(), notNullValue());
    }

    @Test
    public void generalOSMetricsAreCreated() {
        MeterRegistry registry = new SimpleMeterRegistry();

        new OperatingSystemMetrics().bindTo(registry);
        assertThat(registry.find("os.process.cpu.load").gauge(), notNullValue());
        assertThat(registry.find("os.process.cpu.time").gauge(), notNullValue());
        assertThat(registry.find("os.system.cpu.load").gauge(), notNullValue());
        assertThat(registry.find("os.memory.physical.free").gauge(), notNullValue());
        assertThat(registry.find("os.memory.physical.total").gauge(), notNullValue());
        assertThat(registry.find("os.memory.virtual.committed").gauge(), notNullValue());
        assertThat(registry.find("os.swapSpace.free").gauge(), notNullValue());
        assertThat(registry.find("os.swapSpace.total").gauge(), notNullValue());
    }
}
