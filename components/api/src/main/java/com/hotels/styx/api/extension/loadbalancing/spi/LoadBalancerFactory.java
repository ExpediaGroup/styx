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
package com.hotels.styx.api.extension.loadbalancing.spi;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.configuration.Configuration;

/**
 * A factory to create {@link LoadBalancer} instances based on the {@link Environment}.
 *
 * @see LoadBalancer
 * @see Environment
 */
public interface LoadBalancerFactory {
    /**
     * Creates a strategy.
     *
     * @param environment           Styx application environment
     * @param strategyConfiguration configuration specific to load balancer
     * @return a new load balancing strategy.
     */
    LoadBalancer create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins);
}
