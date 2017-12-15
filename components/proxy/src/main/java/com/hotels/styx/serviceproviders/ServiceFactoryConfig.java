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
import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.proxy.ClassFactories.newInstance;

/**
 * A class, intended to be produced by a YAML-parser, that will configure a factory.
 */
public class ServiceFactoryConfig {
    private final boolean enabled;
    private final ServiceFactory<?> factory;
    private final JsonNodeConfig config;
    private final String info;

    ServiceFactoryConfig(@JsonProperty("enabled") Boolean enabled,
                         @JsonProperty("class") String factory,
                         @JsonProperty("config") JsonNode config) {
        this.enabled = enabled == null ? true : enabled;

        this.info = "factory=" + factory + ", config=" + config;

        if (this.enabled) {
            this.factory = newInstance(factory, ServiceFactory.class);
            this.config = new JsonNodeConfig(config);
        } else {
            this.factory = null;
            this.config = null;
        }
    }

    boolean enabled() {
        return enabled;
    }


    /**
     * Creates the service.
     *
     * @param environment       environment
     * @param serviceSuperclass class that the service must extend
     * @param parameters        optional service type dependencies
     * @param <T>               service type
     * @return service
     */
    public <T> T loadService(Environment environment, Class<T> serviceSuperclass, Object... parameters) {
        checkEnabled();

        try {
            return serviceSuperclass.cast(factory.create(environment, config, parameters));
        } catch (Exception e) {
            throw new ConfigurationException("Error creating service", e);
        }
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Cannot load disabled service " + info);
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("factory", factory)
                .add("config", config)
                .toString();
    }
}
