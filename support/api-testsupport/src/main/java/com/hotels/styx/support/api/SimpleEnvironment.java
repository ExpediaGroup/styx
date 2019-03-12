/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.support.api;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.EventSystem;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.configstore.ConfigStore;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static java.util.Objects.requireNonNull;

/**
 * An immutable implementation of {@link Environment} that is created by using a builder to set the properties.
 */
public final class SimpleEnvironment implements Environment {
    private static final CodaHaleMetricRegistry DEFAULT_METRIC_REGISTRY = new CodaHaleMetricRegistry();

    private final Configuration config;
    private final MetricRegistry metricRegistry;
    private final EventSystem eventSystem;

    private SimpleEnvironment(Builder builder) {
        this.config = requireNonNull(builder.config);
        this.metricRegistry = requireNonNull(builder.metricRegistry);
        this.eventSystem = requireNonNull(builder.eventSystem);
    }

    @Override
    public Configuration configuration() {
        return config;
    }

    @Override
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    @Override
    public EventSystem eventSystem() {
        return eventSystem;
    }

    /**
     * Builder for {@link SimpleEnvironment}.
     */
    public static final class Builder {
        private Configuration config = EMPTY_CONFIGURATION;
        private MetricRegistry metricRegistry = DEFAULT_METRIC_REGISTRY;
        private EventSystem eventSystem = new ConfigStore();

        /**
         * Set configuration.
         *
         * @param config configuration
         * @return this builder
         */
        public Builder configuration(Configuration config) {
            this.config = config;
            return this;
        }

        /**
         * Set metric registry.
         *
         * @param metricRegistry metric registry
         * @return this builder
         */
        public Builder metricRegistry(MetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;
            return this;
        }

        /**
         * TODO description.
         *
         * @param eventSystem todo
         * @return todo
         */
        public Builder eventSystem(EventSystem eventSystem) {
            this.eventSystem = requireNonNull(eventSystem);
            return this;
        }

        /**
         * Build an environment using the properties set in this builder.
         *
         * @return a new Environment object.
         */
        public Environment build() {
            return new SimpleEnvironment(this);
        }
    }
}
