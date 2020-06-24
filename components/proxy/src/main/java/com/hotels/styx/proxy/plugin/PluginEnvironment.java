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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.api.plugins.spi.PluginMeterRegistry;
import com.hotels.styx.spi.config.SpiExtension;
import io.micrometer.core.instrument.MeterRegistry;

import static com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry.name;
import static java.util.Objects.requireNonNull;

class PluginEnvironment implements PluginFactory.Environment {
    private final Environment environment;
    private final SpiExtension spiExtension;
    private final MetricRegistry pluginMetricsScope;
    private final PluginMeterRegistry pluginMeterRegistry;

    PluginEnvironment(String name, Environment environment, SpiExtension spiExtension, String scope) {
        this.spiExtension = requireNonNull(spiExtension);
        this.environment = requireNonNull(environment);
        this.pluginMetricsScope = environment.metricRegistry().scope(name(scope, name));
        this.pluginMeterRegistry = new PluginMeterRegistry(requireNonNull(environment.meterRegistry()), name);
    }

    @Override
    public Configuration configuration() {
        return environment.configuration();
    }

    @Override
    public MetricRegistry metricRegistry() {
        return pluginMetricsScope;
    }

    /**
     * @deprecated deprecated in favor of {@link #pluginMeterRegistry()}
     */
    @Deprecated
    @Override
    public MeterRegistry meterRegistry() {
        return environment.meterRegistry();
    }

    @Override
    public <T> T pluginConfig(Class<T> clazz) {
        return spiExtension.config(clazz);
    }

    @Override
    public PluginMeterRegistry pluginMeterRegistry() {
        return pluginMeterRegistry;
    }
}
