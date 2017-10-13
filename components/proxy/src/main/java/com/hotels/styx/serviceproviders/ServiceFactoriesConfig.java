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
package com.hotels.styx.serviceproviders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.Environment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toMap;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A class, intended to be instantiated by a YAML-parser, that will configure a list of factories.
 */
class ServiceFactoriesConfig {
    private static final Logger LOGGER = getLogger(ServiceFactoriesConfig.class);

    private final Map<String, ServiceFactoryConfig> factories;

    ServiceFactoriesConfig(@JsonProperty("factories") Map<String, ServiceFactoryConfig> factories) {
        this.factories = checkNotNull(factories, "No list of all factories specified");
    }

    List<String> names() {
        return new ArrayList<>(factories.keySet());
    }

    /**
     * Creates the services.
     *
     * @param environment       environment
     * @param serviceSuperclass class that the services must extend
     * @param <T>               service type
     * @return services
     */
    <T> Map<String, T> loadServices(Environment environment, Class<? extends T> serviceSuperclass) {
        return factories.entrySet().stream()
                .peek(ServiceFactoriesConfig::log)
                .filter(ServiceFactoriesConfig::serviceEnabled)
                .collect(convertValue(factoryConfig ->
                        factoryConfig.loadService(environment, serviceSuperclass)));
    }

    private static void log(Map.Entry<String, ServiceFactoryConfig> service) {
        if (serviceEnabled(service)) {
            LOGGER.info("Service '{}' is ENABLED", service.getKey());
        } else {
            LOGGER.info("Service '{}' is DISABLED", service.getKey());
        }
    }

    private static boolean serviceEnabled(Map.Entry<?, ServiceFactoryConfig> entry) {
        return entry.getValue().enabled();
    }

    private static <K, T, R> Collector<Map.Entry<K, T>, ?, Map<K, R>> convertValue(Function<T, R> function) {
        return toMap(Map.Entry::getKey, entry -> function.apply(entry.getValue()));
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("factories", factories)
                .toString();
    }
}
