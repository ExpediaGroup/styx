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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public class PluginMeterRegistry {
    private final MeterRegistry registry;
    private final Tag pluginNameTag;

    public PluginMeterRegistry(MeterRegistry registry, String pluginName) {
        this.registry = registry;
        this.pluginNameTag = Tag.of("plugin", pluginName);
    }

    public List<Meter> getMeters() {
        return registry.getMeters();
    }

    public void forEachMeter(Consumer<? super Meter> consumer) {
        registry.forEachMeter(consumer);
    }

    public MeterRegistry.Config config() {
        return registry.config();
    }

    public Search find(String name) {
        return registry.find(name);
    }

    public RequiredSearch get(String name) {
        return registry.get(name);
    }

    public Counter counter(String name, Iterable<Tag> tags) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.counter(name, newTags);
    }

    public Counter counter(String name, String... tags) {
        String[] newTags = injectPluginNameTag(tags);
        return registry.counter(name, newTags);
    }

    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.summary(name, newTags);
    }

    public DistributionSummary summary(String name, String... tags) {
        String[] newTags = injectPluginNameTag(tags);
        return registry.summary(name, newTags);
    }

    public Timer timer(String name, Iterable<Tag> tags) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.timer(name, newTags);
    }

    public Timer timer(String name, String... tags) {
        String[] newTags = injectPluginNameTag(tags);
        return registry.timer(name, newTags);
    }

    public MeterRegistry.More more() {
        return registry.more();
    }

    @Nullable
    public <T> T gauge(String name, Iterable<Tag> tags, T stateObject, ToDoubleFunction<T> valueFunction) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.gauge(name, newTags, stateObject, valueFunction);
    }

    @Nullable
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.gauge(name, newTags, number);
    }

    @Nullable
    public <T extends Number> T gauge(String name, T number) {
        return registry.gauge(name, Tags.of(pluginNameTag), number);
    }

    @Nullable
    public <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction) {
        return registry.gauge(name, Tags.of(pluginNameTag), stateObject, valueFunction);
    }

    @Nullable
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.gaugeCollectionSize(name, newTags, collection);
    }

    @Nullable
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        Iterable<Tag> newTags = injectPluginNameTag(tags);
        return registry.gaugeMapSize(name, newTags, map);
    }

    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter meter) {
        return registry.remove(meter);
    }

    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter.Id mappedId) {
        return registry.remove(mappedId);
    }

    @Incubating(since = "1.2.0")
    public void clear() {
        registry.clear();
    }

    public void close() {
        registry.close();
    }

    public boolean isClosed() {
        return registry.isClosed();
    }

    private Iterable<Tag> injectPluginNameTag(Iterable<Tag> tags) {
        Collection<Tag> result = new ArrayList<>();
        tags.forEach(result::add);
        result.add(pluginNameTag);

        return result;
    }

    private String[] injectPluginNameTag(final String[] tags) {
        return Stream.of(tags, new String[]{pluginNameTag.getKey(), pluginNameTag.getValue()})
                .flatMap(Stream::of)
                .toArray(String[]::new);
    }
}
