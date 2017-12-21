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
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.configuration.Configuration;

import static java.util.Collections.emptyList;

/**
 * A factory to create {@link LoadBalancingStrategy} instances based on the {@link Environment}.
 *
 * @see LoadBalancingStrategy
 * @see Environment
 */
public interface LoadBalancingStrategyFactory extends ServiceFactory<LoadBalancingStrategy> {

    /**
     * LoadBalancingStrategy requires {@link ActiveOrigins} to perform ordering of origins, so this method
     * doesn't make much sense in that context.
     *
     * @param environment           Styx application environment
     * @param strategyConfiguration configuration specific to load balancer
     * @return strategy that is returning an empty collection.
     */
    default LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration) {
        return context -> emptyList();
    }

    /**
     * Creates a strategy.
     *
     * @param environment           Styx application environment
     * @param strategyConfiguration configuration specific to load balancer
     * @return a new load balancing strategy.
     */
    LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins);
}
