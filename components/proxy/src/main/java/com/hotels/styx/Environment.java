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
package com.hotels.styx;

import com.google.common.eventbus.EventBus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.common.format.DefaultHttpMessageFormatter;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.proxy.HttpErrorStatusCauseLogger;
import com.hotels.styx.proxy.HttpErrorStatusMetrics;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.ServerEnvironment;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Environment: metrics, health check, build info, event bus.
 */
public final class Environment implements com.hotels.styx.api.Environment {
    private final Version version;
    private final EventBus eventBus;
    private final List<NamedPlugin> plugins;
    private final StyxConfig configuration;
    private final HttpErrorStatusListener httpErrorStatusListener;
    private final ServerEnvironment serverEnvironment;
    private final HttpMessageFormatter httpMessageFormatter;

    private Environment(Builder builder) {
        this.eventBus = firstNonNull(builder.eventBus, () -> new EventBus("Styx"));
        this.plugins = new ArrayList<>();

        this.configuration = builder.configuration;
        this.version = firstNonNull(builder.version, Version::newVersion);
        this.serverEnvironment = new ServerEnvironment(builder.registry);
        this.httpMessageFormatter = builder.httpMessageFormatter;

        this.httpErrorStatusListener = HttpErrorStatusListener.compose(
                new HttpErrorStatusCauseLogger(httpMessageFormatter),
                new HttpErrorStatusMetrics(serverEnvironment.metricRegistry()));
    }

    // prevent unnecessary construction of defaults
    private static <T> T firstNonNull(T one, Supplier<T> two) {
        return one != null ? one : two.get();
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public List<NamedPlugin> plugins() {
        return plugins;
    }

    public Version buildInfo() {
        return version;
    }

    public HttpErrorStatusListener errorListener() {
        return this.httpErrorStatusListener;
    }

    @Override
    public StyxConfig configuration() {
        return this.configuration;
    }

    @Override
    public MetricRegistry metricRegistry() {
        return serverEnvironment.metricRegistry();
    }

    @Override
    public MeterRegistry meterRegistry() { return serverEnvironment.registry(); }

    /**
     * @deprecated Use {@link #configuration()}
     *
     * @return configuration
     */
    @Deprecated
    public StyxConfig styxConfig() {
        return configuration();
    }

    public ServerEnvironment serverEnvironment() {
        return serverEnvironment;
    }

    public HttpMessageFormatter httpMessageFormatter() {
        return httpMessageFormatter;
    }

    /**
     * Builder for {@link com.hotels.styx.Environment}.
     */
    public static class Builder {
        private MeterRegistry registry;
        private Version version;
        private EventBus eventBus;
        private StyxConfig configuration = StyxConfig.defaultConfig();
        private HttpMessageFormatter httpMessageFormatter = new DefaultHttpMessageFormatter();

        public Builder configuration(StyxConfig configuration) {
            this.configuration = requireNonNull(configuration);
            return this;
        }

        public Builder registry(MeterRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder buildInfo(Version version) {
            this.version = version;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder httpMessageFormatter(HttpMessageFormatter httpMessageFormatter) {
            this.httpMessageFormatter = requireNonNull(httpMessageFormatter);
            return this;
        }

        public Environment build() {
            if (registry == null) {
                throw new IllegalStateException("Meter registry must be specified");
            }
            return new Environment(this);
        }
    }
}
