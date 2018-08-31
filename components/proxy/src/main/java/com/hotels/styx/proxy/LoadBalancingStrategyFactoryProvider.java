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
package com.hotels.styx.proxy;

import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.MissingConfigurationException;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static com.hotels.styx.proxy.ClassFactories.newInstance;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Create the load balancing strategy factory from the configuration.
 */
public class LoadBalancingStrategyFactoryProvider implements Supplier<LoadBalancerFactory> {
    private static final String LOAD_BALANCING_STRATEGY_KEY = "loadBalancing.strategy";
    private static final LoadBalancerFactory BUSY_CONNECTION_BALANCER = new BusyConnectionsStrategy.Factory();

    private static final Logger LOGGER = getLogger(LoadBalancingStrategyFactoryProvider.class);

    private final Configuration configurations;

    public LoadBalancingStrategyFactoryProvider(Configuration configurations) {
        this.configurations = configurations;
    }

    public static LoadBalancingStrategyFactoryProvider newProvider(Configuration configurations) {
        return new LoadBalancingStrategyFactoryProvider(configurations);
    }

    @Override
    public LoadBalancerFactory get() {
        return configurations.get(LOAD_BALANCING_STRATEGY_KEY)
                .map(this::newFactoryInstance)
                .orElse(busyConnectionBalancer());
    }

    private LoadBalancerFactory busyConnectionBalancer() {
        LOGGER.info("No configured load-balancing strategy found. Using {}", BUSY_CONNECTION_BALANCER);
        return BUSY_CONNECTION_BALANCER;
    }

    private LoadBalancerFactory newFactoryInstance(String strategyName) {
        String factoryClassName = factoryClassName(strategyName);
        return newInstance(factoryClassName, LoadBalancerFactory.class);
    }

    private String factoryClassName(String strategyName) {
        String key = format("loadBalancing.strategies.%s.factory.class", strategyName);

        return configurations.get(key).orElseThrow(() -> new MissingConfigurationException(key));
    }
}
