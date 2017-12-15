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
package com.hotels.styx.api.client.loadbalancing.spi;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.configuration.Configuration;

/**
 * A factory to create {@link LoadBalancingStrategy} instances based on the {@link Environment}.
 *
 * @see LoadBalancingStrategy
 * @see Environment
 */
public interface LoadBalancingStrategyFactory extends ServiceFactory<LoadBalancingStrategy> {

    /**
     * Creates a strategy.
     *
     * @param environment           Styx application environment
     * @param strategyConfiguration configuration specific to load balancer
     * @return a new load balancing strategy.
     */
    default LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration) {
        return create(environment, strategyConfiguration, new Object[0]);
    }

    /**
     * Creates a strategy.
     *
     * @param environment           Styx application environment
     * @param strategyConfiguration configuration specific to load balancer
     * @return a new load balancing strategy.
     */
    LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, Object... originsInventory);
}
