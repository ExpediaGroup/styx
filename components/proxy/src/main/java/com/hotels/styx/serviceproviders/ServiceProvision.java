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


import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.configuration.Configuration;

import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyMap;

/**
 * Loads service classes from service definition configurations.
 */
public final class ServiceProvision {
    private ServiceProvision() {
    }

    /**
     * Create a {@link com.hotels.styx.client.StyxHttpClient} related factory configured with a particular key,
     * then uses the factory's create method to create its product.
     *
     * @param <E>            service type
     *
     * @param configuration  Styx configuration
     * @param key            Factory configuration attribute
     * @param serviceClass   Service class
     * @param activeOrigins  source of active connections for purpose of load balancing
     *
     * @return service, if such a configuration key exists
     * */
    public static <E> Optional<E> loadService(Configuration configuration, Environment environment, String key,
                                              Class<? extends E> serviceClass, ActiveOrigins activeOrigins) {
        return configuration
                .get(key, ServiceFactoryConfig.class)
                .map(factoryConfig -> factoryConfig.loadService(environment, serviceClass, activeOrigins));
    }

    /**
     * Create factory configured with a particular key, then uses the factory's create method
     * to create its product.
     *
     * @param <E>            service type
     *
     * @param configuration  Styx configuration
     * @param key            Factory configuration attribute
     * @param serviceClass   Service class
     *
     * @return service, if such a configuration key exists
     * */
    public static <E> Optional<E> loadService(Configuration configuration, Environment environment, String key,
                                              Class<? extends E> serviceClass) {
        return configuration
                .get(key, ServiceFactoryConfig.class)
                .map(factoryConfig -> factoryConfig.loadService(environment, serviceClass));
    }

    /**
     * Creates the services whose configuration has the specified key.
     *
     * @param <T>            service type
     *
     * @param configuration  Styx configuration
     * @param key            Factory configuration attribute
     * @param serviceClass   Service class
     *
     * @return services, if such a configuration key exists
     */
    public static <T> Map<String, T> loadServices(Configuration configuration, Environment environment, String key, Class<? extends T> serviceClass) {
        return configuration
                .get(key, ServiceFactoriesConfig.class)
                .<Map<String, T>>map(factoriesConfig -> factoriesConfig.loadServices(environment, serviceClass))
                .orElse(emptyMap());
    }
}
