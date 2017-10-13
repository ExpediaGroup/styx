/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.infrastructure.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationFactory;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provider of {@code Configuration} and {@code com.hotels.styx.api.configuration.ConfigurationContextResolver}.
 */
public final class ConfigurationSupplier implements Supplier<Configuration> {
    private static final Logger LOGGER = getLogger(ConfigurationSupplier.class);

    private static final Optional<ConfigurationFactory> EMPTY_CONFIGURATION_FACTORY = Optional.of(ConfigurationFactory.EMPTY_CONFIGURATION_FACTORY);

    private final Configuration configuration;

    public ConfigurationSupplier(Configuration configuration) {
        this.configuration = checkNotNull(configuration);
    }

    @Override
    public Configuration get() {
        ConfigurationFactory configurationFactory = configuration.get("configuration", ConfigurationConfig.class)
                .map(ConfigurationSupplier::createConfigurationFactory)
                .orElse(EMPTY_CONFIGURATION_FACTORY)
                .get();

        return configurationFactory.create(configuration);
    }

    private static Optional<ConfigurationFactory> createConfigurationFactory(ConfigurationConfig configurationConfig) {
        return createConfigurationFactory(configurationConfig.factory);
    }

    private static Optional<ConfigurationFactory> createConfigurationFactory(ObjectFactory factory) {
        Optional<ConfigurationFactory> cf = factory.newInstance(ConfigurationFactory.class);
        LOGGER.info("Loaded a configuration factory={} from metadata={}", cf, factory);
        return cf;
    }

    private static class ConfigurationConfig {
        @JsonProperty("factory")
        ObjectFactory factory;
    }
}
