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
package com.hotels.styx.metrics.reporting.jmx;

import com.hotels.styx.api.extension.service.spi.StyxService;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxMeterRegistry;

import java.util.concurrent.CompletableFuture;

import static java.lang.String.join;
import static java.util.Objects.requireNonNull;

/**
 * Builds JMX reporter from configuration and wraps it in service interface.
 */
public class JmxReporterService implements StyxService {

    private final HierarchicalNameMapper nameMapper;
    private final MeterRegistry meterRegistry;
    private JmxMeterRegistry jmxMeterRegistry;

    public JmxReporterService(String domain, MeterRegistry registry) {
        this.nameMapper = (id, convention) ->
                join(".", requireNonNull(domain), HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, convention));
        this.meterRegistry = requireNonNull(registry);
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            jmxMeterRegistry = new JmxMeterRegistry(key -> null, Clock.SYSTEM, nameMapper);
            ((CompositeMeterRegistry) meterRegistry).add(jmxMeterRegistry);
        });
    }

    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            jmxMeterRegistry.stop();
            ((CompositeMeterRegistry) meterRegistry).remove(jmxMeterRegistry);
        });
    }
}
