/**
 * Copyright (C) 2013-2018 Expedia Inc.
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


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.spi.config.ServiceFactoryConfig;
import com.hotels.styx.spi.config.SpiExtension;

import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.proxy.ClassFactories.newInstance;
import static com.hotels.styx.serviceproviders.ServiceProvision.Pair.pair;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

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
     * @param <E>           service type
     * @param configuration Styx configuration
     * @param key           Factory configuration attribute
     * @param serviceClass  Service class
     * @param activeOrigins source of active connections for purpose of load balancing
     * @return service, if such a configuration key exists
     */
    public static <E> Optional<E> loadLoadBalancer(Configuration configuration, Environment environment, String key,
                                                   Class<? extends E> serviceClass, ActiveOrigins activeOrigins) {
        return configuration
                .get(key, ServiceFactoryConfig.class)
                .map(factoryConfig -> {
                    try {
                        LoadBalancerFactory factory = newInstance(factoryConfig.factory(), LoadBalancerFactory.class);
                        return serviceClass.cast(factory.create(environment, factoryConfig.config(), activeOrigins));
                    } catch (Exception e) {
                        throw new ConfigurationException("Error creating service", e);
                    }
                });
    }

    /**
     * Create factory configured with a particular key, then uses the factory's create method
     * to create its product.
     *
     * @param <E>           service type
     * @param configuration Styx configuration
     * @param key           Factory configuration attribute
     * @param serviceClass  Service class
     * @return service, if such a configuration key exists
     */
    public static <E> Optional<E> loadService(Configuration configuration, Environment environment, String key,
                                              Class<? extends E> serviceClass) {
        return configuration
                .get(key, ServiceFactoryConfig.class)
                .map(factoryConfig -> loadServiceFactory(factoryConfig, environment, serviceClass));
    }

    /**
     * Creates the services whose configuration has the specified key.
     *
     * @param <T>           service type
     * @param configuration Styx configuration
     * @param key           Factory configuration attribute
     * @param serviceClass  Service class
     * @return services, if such a configuration key exists
     */
    public static <T> Map<String, T> loadServices(Configuration configuration, Environment environment, String key, Class<? extends T> serviceClass) {
        return configuration.get(key, JsonNode.class)
                .<Map<String, T>>map(node -> servicesMap(node, environment, serviceClass))
                .orElse(emptyMap());
    }

    private static <U> Map<String, U> servicesMap(JsonNode jsonNode, Environment environment, Class<? extends U> serviceClass) {
//        Optional<JsonNode> factories = jsonNode.get("factories", JsonNode.class);
//        if (!jsonNode.has("factories")) {
//            throw new RuntimeException("Expecting a 'factories' keyword");
//        }

        JsonNode factories = jsonNode.get("factories");
        JsonNodeConfig jsonNodeConfig = new JsonNodeConfig(factories);

        return newArrayList(factories.fieldNames())
                .stream()
                .flatMap(name -> {
                    try {
                        return jsonNodeConfig.get(name, SpiExtension.class)
                                .filter(SpiExtension::enabled)
                                .map(extension -> loadSpiExtension(extension, environment, serviceClass))
                                .map(service -> ImmutableList.of(pair(name, service)))
                                .orElse(ImmutableList.of())
                                .stream();
                    } catch (Exception e) {
                        return jsonNodeConfig.get(name, ServiceFactoryConfig.class)
                                .filter(ServiceFactoryConfig::enabled)
                                .map(serviceFactoryConfig -> loadServiceFactory(serviceFactoryConfig, environment, serviceClass))
                                .map(service -> ImmutableList.of(pair(name, service)))
                                .orElse(ImmutableList.of())
                                .stream();
                    }
                })
                .collect(toMap(Pair::key, Pair::value));

    }

    private static <T> T loadSpiExtension(SpiExtension factoryConfig, Environment environment, Class<T> serviceSuperclass) {
            ServiceFactory factory = newInstance(factoryConfig.factory().factoryClass(), ServiceFactory.class);
            JsonNodeConfig config = new JsonNodeConfig(factoryConfig.config());

            return serviceSuperclass.cast(factory.create(environment, config));
    }

    private static <T> T loadServiceFactory(ServiceFactoryConfig serviceFactoryConfig, Environment environment, Class<T> serviceSuperclass) {
        ServiceFactory factory = newInstance(serviceFactoryConfig.factory(), ServiceFactory.class);
        JsonNodeConfig config = serviceFactoryConfig.config();

        return serviceSuperclass.cast(factory.create(environment, config));
    }

    static class Pair<K, V> {
        private final K key;
        private final V value;

        private Pair(K key, V value) {
            this.key = requireNonNull(key);
            this.value = requireNonNull(value);
        }

        public static <K, V> Pair<K, V> pair(K key, V value) {
            return new Pair<>(key, value);
        }

        K key() {
            return key;
        }

        V value() {
            return value;
        }
    }

}
