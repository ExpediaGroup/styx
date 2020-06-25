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

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class PluginMeterRegistry {
    public static final String DEFAULT_TAG_KEY = "plugin";
    private final MeterRegistry meterRegistry;
    private final Tag defaultTag;
    private Tags commonTags;

    public PluginMeterRegistry(MeterRegistry meterRegistry, String pluginName) {
        this(meterRegistry, pluginName, Tags.empty());
    }

    public PluginMeterRegistry(MeterRegistry meterRegistry, String pluginName, Tags commonTags) {
        this.meterRegistry = meterRegistry;
        this.defaultTag = Tag.of(DEFAULT_TAG_KEY, pluginName);
        this.commonTags = commonTags;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public Tags getCommonTags() {
        return commonTags;
    }

    public void setCommonTags(Tags commonTags) {
        this.commonTags = commonTags;
    }

    public Tag getDefaultTag() {
        return defaultTag;
    }

    public Collection<Meter> getMeters() {
        return Search.in(meterRegistry).tags(Tags.of(defaultTag)).meters();
    }

    public void forEachMeter(Consumer<? super Meter> consumer) {
        getMeters().forEach(consumer);
    }

    public MeterRegistry.Config config() {
        return meterRegistry.config();
    }

    public Search find(String name) {
        return Search.in(meterRegistry).tags(Tags.of(defaultTag)).name(name);
    }

    public RequiredSearch get(String name) {
        return RequiredSearch.in(meterRegistry).tags(Tags.of(defaultTag)).name(name);
    }

    public Counter counter(String name, Iterable<Tag> tags) {
        return meterRegistry.counter(name, commonTags.and(defaultTag).and(tags));
    }

    public Counter counter(String name, String... tags) {
        return meterRegistry.counter(name, commonTags.and(defaultTag).and(tags));
    }

    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return meterRegistry.summary(name, commonTags.and(defaultTag).and(tags));
    }

    public DistributionSummary summary(String name, String... tags) {
        return meterRegistry.summary(name, commonTags.and(defaultTag).and(tags));
    }

    public Timer timer(String name, Iterable<Tag> tags) {
        return meterRegistry.timer(name, commonTags.and(defaultTag).and(tags));
    }

    public Timer timer(String name, String... tags) {
        return meterRegistry.timer(name, commonTags.and(defaultTag).and(tags));
    }

    @Nullable
    public <T> T gauge(String name, Iterable<Tag> tags, T stateObject, ToDoubleFunction<T> valueFunction) {
        return meterRegistry.gauge(name, commonTags.and(defaultTag).and(tags), stateObject, valueFunction);
    }

    @Nullable
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return meterRegistry.gauge(name, commonTags.and(defaultTag).and(tags), number);
    }

    @Nullable
    public <T extends Number> T gauge(String name, T number) {
        return meterRegistry.gauge(name, commonTags.and(defaultTag), number);
    }

    @Nullable
    public <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction) {
        return meterRegistry.gauge(name, commonTags.and(defaultTag), stateObject, valueFunction);
    }

    @Nullable
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return meterRegistry.gaugeCollectionSize(name, commonTags.and(defaultTag).and(tags), collection);
    }

    @Nullable
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return meterRegistry.gaugeMapSize(name, commonTags.and(defaultTag).and(tags), map);
    }

    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter meter) {
        return meterRegistry.remove(meter);
    }

    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter.Id mappedId) {
        return meterRegistry.remove(mappedId);
    }

    @Incubating(since = "1.2.0")
    public void clear() {
        forEachMeter(this::remove);
    }
}
