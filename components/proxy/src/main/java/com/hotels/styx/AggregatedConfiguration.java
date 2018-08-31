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
package com.hotels.styx;

import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConversionException;

import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Aggregate of StyxConfig and a Configuration object.
 *
 * @deprecated We don't seem to have a real use-case for this
 */
@Deprecated
public final class AggregatedConfiguration implements Configuration {
    private final StyxConfig styxConfig;
    private final Configuration configuration;

    public AggregatedConfiguration(StyxConfig styxConfig) {
        this(styxConfig, EMPTY_CONFIGURATION);
    }

    AggregatedConfiguration(StyxConfig styxConfig, Configuration configuration) {
        this.styxConfig = requireNonNull(styxConfig);
        this.configuration = requireNonNull(configuration);
    }

    public StyxConfig styxConfig() {
        return styxConfig;
    }

    @Override
    public Optional<String> get(String key) {
        return styxConfig.get(key);
    }

    @Override
    public <X> Optional<X> get(String key, Class<X> type) {
        return styxConfig.get(key, type);
    }

    public int port() {
        return styxConfig.port();
    }

    public String logConfigLocation() {
        return styxConfig.logConfigLocation();
    }

    @Override
    public <X> X as(Class<X> type) throws ConversionException {
        return styxConfig.as(type);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("styxConfig", styxConfig)
                .add("configuration", configuration)
                .toString();
    }
}
