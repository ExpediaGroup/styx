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
package com.hotels.styx.api.plugins.spi;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PluginMeterRegistryTest {
    private static final String PLUGIN_NAME = "TestPlugin";
    private static final String NON_PLUGIN_METER_NAME = "unrelated.meter";
    private static final String PLUGIN_METER_NAME = "plugin.meter";
    private MeterRegistry backingRegistry;
    private PluginMeterRegistry pluginMeterRegistry;

    @BeforeEach
    void setUp() {
        backingRegistry = new SimpleMeterRegistry();
        pluginMeterRegistry = new PluginMeterRegistry(backingRegistry, PLUGIN_NAME);
    }

    @AfterEach
    void tearDown() {
        backingRegistry.clear();
    }

    @Test
    void getMeterRegistry() {
        assertEquals(backingRegistry, pluginMeterRegistry.getMeterRegistry());
    }

    @Test
    void getMetersShouldOnlyReturnMetersAssociatedWithPlugin() {
        Counter unrelatedCounter = backingRegistry.counter(NON_PLUGIN_METER_NAME);
        Counter pluginCounter = pluginMeterRegistry.counter(PLUGIN_METER_NAME);

        Collection<Meter> pluginMeters = pluginMeterRegistry.getMeters();

        assertEquals(1, pluginMeters.size());
        assertFalse(pluginMeters.contains(unrelatedCounter));
        assertTrue(pluginMeters.contains(pluginCounter));
    }

    @Test
    void forEachMeterShouldOnlyAffectPluginMeters() {
        Counter unrelatedCounter = backingRegistry.counter(NON_PLUGIN_METER_NAME);
        Counter pluginCounter = pluginMeterRegistry.counter(PLUGIN_METER_NAME, Tags.empty());

        unrelatedCounter.increment();
        pluginCounter.increment();

        assertEquals(1.0, unrelatedCounter.count());
        assertEquals(1.0, pluginCounter.count());

        pluginMeterRegistry.forEachMeter(meter -> ((Counter)meter).increment());

        assertEquals(1.0, unrelatedCounter.count());
        assertEquals(2.0, pluginCounter.count());
    }

    @Test
    void findShouldOnlySearchInPluginMeters() {
        backingRegistry.gauge(NON_PLUGIN_METER_NAME, 0D);
        pluginMeterRegistry.gauge(PLUGIN_METER_NAME, Tags.empty(),  0D);

        assertEquals(0,pluginMeterRegistry.find(NON_PLUGIN_METER_NAME).gauges().size());
        assertEquals(1,backingRegistry.find(PLUGIN_METER_NAME).gauges().size());
        assertEquals(1,pluginMeterRegistry.find(PLUGIN_METER_NAME).gauges().size());
    }

    @Test
    void getShouldOnlyReturnPluginMeters() {
        backingRegistry.gauge(NON_PLUGIN_METER_NAME, 0D);
        pluginMeterRegistry.gauge(PLUGIN_METER_NAME, Tags.empty(), 0D);

        assertThrows(MeterNotFoundException.class, () -> pluginMeterRegistry.get(NON_PLUGIN_METER_NAME).gauge());
        assertNotNull(backingRegistry.get(PLUGIN_METER_NAME).gauge());
        assertNotNull(pluginMeterRegistry.get(PLUGIN_METER_NAME).gauge());
    }

    @Test
    void clearShouldOnlyRemovePluginMeters() {
        Timer unrelatedTimer = backingRegistry.timer(NON_PLUGIN_METER_NAME);
        Timer pluginTimer = pluginMeterRegistry.timer(PLUGIN_METER_NAME, Tags.empty());

        assertEquals(2, backingRegistry.getMeters().size());

        pluginMeterRegistry.clear();

        assertEquals(1, backingRegistry.getMeters().size());
        assertEquals(0, pluginMeterRegistry.getMeters().size());
    }

    @Test
    void createdPluginMetersShouldIncludeDefaultTag() {
        pluginMeterRegistry.counter("counter");
        pluginMeterRegistry.timer("timer");
        pluginMeterRegistry.gauge("gauge", 0D);
        pluginMeterRegistry.summary("summary", Tags.empty());

        backingRegistry.getMeters().forEach(meter -> {
            assertTrue(meter.getId().getTags().contains(pluginMeterRegistry.getDefaultPluginTag().iterator().next()));
        });
    }

    @Test
    void createdPluginMetersShouldIncludeCommonTags() {
        pluginMeterRegistry = new PluginMeterRegistry(backingRegistry, PLUGIN_NAME, Tags.of("Common", "Tags"));

        Timer timer = pluginMeterRegistry.timer("timer");
        Double gauge = pluginMeterRegistry.gauge("gauge", 0D);
        Counter counter = pluginMeterRegistry.counter("counter");
        DistributionSummary summary = pluginMeterRegistry.summary("summary");
        Collection<String> collectionGauge = pluginMeterRegistry.gaugeCollectionSize(PLUGIN_METER_NAME, Tags.empty(), new ArrayList<>());
        Map<String, String> mapGauge = pluginMeterRegistry.gaugeMapSize(PLUGIN_METER_NAME, Tags.empty(), new HashMap<>());

        backingRegistry.getMeters().forEach(meter -> {
            final List<Tag> meterTags = meter.getId().getTags();

            assertTrue(meterTags.contains(pluginMeterRegistry.getDefaultPluginTag().iterator().next()));
            assertTrue(meterTags.contains(Tag.of("Common", "Tags")));
        });
    }
}
