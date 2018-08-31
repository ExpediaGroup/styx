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
package com.hotels.styx.serviceproviders;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicy;
import com.hotels.styx.api.extension.retrypolicy.spi.RetryPolicyFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.common.Pair;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.spi.config.ServiceFactoryConfig;
import com.hotels.styx.spi.config.SpiExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.common.Pair.pair;
import static com.hotels.styx.proxy.ClassFactories.newInstance;
import static com.hotels.styx.spi.ExtensionObjectFactory.EXTENSION_OBJECT_FACTORY;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

/**
 * Loads service classes from service definition configurations.
 */
public final class ServiceProvision {
    private ServiceProvision() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceProvision.class);

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
    public static <E extends LoadBalancer> Optional<E> loadLoadBalancer(
            Configuration configuration,
            Environment environment,
            String key,
            Class<? extends E> serviceClass,
            ActiveOrigins activeOrigins) {
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
    public static <E extends RetryPolicy> Optional<E> loadRetryPolicy(
            Configuration configuration,
            Environment environment,
            String key,
            Class<? extends E> serviceClass) {
        return configuration
                .get(key, ServiceFactoryConfig.class)
                .map(factoryConfig -> {
                    RetryPolicyFactory factory = newInstance(factoryConfig.factory(), RetryPolicyFactory.class);
                    JsonNodeConfig config = factoryConfig.config();

                    return serviceClass.cast(factory.create(environment, config));
                });
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
        JsonNode factories = jsonNode.get("factories");
        JsonNodeConfig jsonNodeConfig = new JsonNodeConfig(factories);

        return newArrayList(factories.fieldNames())
                .stream()
                .flatMap(name -> {
                    if (isType(name, jsonNodeConfig, SpiExtension.class)) {
                        return namedExtensionFromSpiExtension(environment, serviceClass, jsonNodeConfig, name);
                    } else if (isType(name, jsonNodeConfig, ServiceFactoryConfig.class)) {
                        return namedExtensionFromServiceFactoryConfig(environment, serviceClass, jsonNodeConfig, name);
                    } else {
                        String content = factories.get(name).toString();
                        String message = format("Unexpected configuration object 'services.factories.%s', Configuration='%s'", name, content);
                        throw new ConfigurationException(message);
                    }
                })
                .collect(toMap(Pair::key, Pair::value));
    }

    private static <T> boolean isType(String name, JsonNodeConfig jsonNodeConfig, Class<T> nodeType) {
        try {
            jsonNodeConfig.get(name, nodeType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static <U> Stream<Pair<String, ? extends U>> namedExtensionFromSpiExtension(
            Environment environment, Class<? extends U> serviceClass, JsonNodeConfig jsonNodeConfig, String name) {
        LOGGER.info("Spi Extension type");
        return jsonNodeConfig.get(name, SpiExtension.class)
                .filter(SpiExtension::enabled)
                .map(extension -> loadSpiExtension(extension, environment, serviceClass))
                .map(service -> ImmutableList.<Pair<String, ? extends U>>of(pair(name, service)))
                .orElse(ImmutableList.of())
                .stream();
    }

    private static <T> T loadSpiExtension(SpiExtension factoryConfig, Environment environment, Class<T> serviceSuperclass) {
        ServiceFactory factory = newServiceFactory(factoryConfig);
        JsonNodeConfig config = new JsonNodeConfig(factoryConfig.config());

        return serviceSuperclass.cast(factory.create(environment, config));
    }

    private static ServiceFactory newServiceFactory(SpiExtension extensionConfig) {
        try {
            return EXTENSION_OBJECT_FACTORY.newInstance(extensionConfig.factory(), ServiceFactory.class);
        } catch (Exception e) {
            throw new ConfigurationException(format("Could not load a service factory for configuration=%s", extensionConfig), e);
        }
    }

    private static <U> Stream<Pair<String, ? extends U>> namedExtensionFromServiceFactoryConfig(
            Environment environment, Class<? extends U> serviceClass, JsonNodeConfig jsonNodeConfig, String name) {
        LOGGER.info("Service Factory Config type");
        return jsonNodeConfig.get(name, ServiceFactoryConfig.class)
                .filter(ServiceFactoryConfig::enabled)
                .map(serviceFactoryConfig -> loadServiceFactory(serviceFactoryConfig, environment, serviceClass))
                .map(service -> ImmutableList.<Pair<String, ? extends U>>of(pair(name, service)))
                .orElse(ImmutableList.of()).stream();
    }

    private static <T> T loadServiceFactory(ServiceFactoryConfig serviceFactoryConfig, Environment environment, Class<T> serviceSuperclass) {
        ServiceFactory factory = newInstance(serviceFactoryConfig.factory(), ServiceFactory.class);
        JsonNodeConfig config = serviceFactoryConfig.config();

        return serviceSuperclass.cast(factory.create(environment, config));
    }
}
