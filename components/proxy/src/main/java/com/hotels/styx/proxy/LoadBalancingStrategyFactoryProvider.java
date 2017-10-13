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
package com.hotels.styx.proxy;

import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.MissingConfigurationException;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static com.hotels.styx.proxy.ClassFactories.newInstance;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Create the load balancing strategy factory from the configuration.
 */
public class LoadBalancingStrategyFactoryProvider implements Supplier<LoadBalancingStrategyFactory> {
    private static final String LOAD_BALANCING_STRATEGY_KEY = "loadBalancing.strategy";
    private static final LoadBalancingStrategyFactory ROUND_ROBIN = new RoundRobinStrategy.Factory();

    private static final Logger LOGGER = getLogger(LoadBalancingStrategyFactoryProvider.class);

    private final Configuration configurations;

    public LoadBalancingStrategyFactoryProvider(Configuration configurations) {
        this.configurations = configurations;
    }

    public static LoadBalancingStrategyFactoryProvider newProvider(Configuration configurations) {
        return new LoadBalancingStrategyFactoryProvider(configurations);
    }

    @Override
    public LoadBalancingStrategyFactory get() {
        return configurations.get(LOAD_BALANCING_STRATEGY_KEY)
                .map(this::newFactoryInstance)
                .orElse(roundRobin());
    }

    private LoadBalancingStrategyFactory roundRobin() {
        LOGGER.info("No configured load-balancing strategy found. Using {}", ROUND_ROBIN);
        return ROUND_ROBIN;
    }

    private LoadBalancingStrategyFactory newFactoryInstance(String strategyName) {
        String factoryClassName = factoryClassName(strategyName);
        return newInstance(factoryClassName, LoadBalancingStrategyFactory.class);
    }

    private String factoryClassName(String strategyName) {
        String key = format("loadBalancing.strategies.%s.factory.class", strategyName);

        return configurations.get(key).orElseThrow(() -> new MissingConfigurationException(key));
    }
}
