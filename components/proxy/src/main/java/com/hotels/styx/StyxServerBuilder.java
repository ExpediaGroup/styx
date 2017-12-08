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
package com.hotels.styx;

import com.hotels.styx.api.service.spi.StyxService;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginSuppliers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.Environments.newEnvironment;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;

/**
 * Builder for StyxServer.
 */
public final class StyxServerBuilder {
    private final Environment environment;
    private final Map<String, StyxService> additionalServices = new HashMap<>();
    private Supplier<Iterable<NamedPlugin>> pluginsSupplier;

    private Consumer<Environment> loggingSetUp = env -> {
    };

    public StyxServerBuilder(StyxConfig styxConfig) {
        this.environment = newEnvironment(styxConfig);
    }

    private static Supplier<Iterable<NamedPlugin>> pluginsSuppliers(Environment environment) {
        return new PluginSuppliers(environment).fromConfigurations();
    }

    Environment getEnvironment() {
        return environment;
    }

    public StyxServerBuilder additionalServices(String name, StyxService service) {
        this.additionalServices.put(name, checkNotNull(service));
        return this;
    }

    public StyxServerBuilder additionalServices(Map<String, StyxService> namedServices) {
        namedServices.forEach(this.additionalServices::put);
        return this;
    }

    public Map<String, StyxService> additionalServices() {
        return this.additionalServices;
    }

    public StyxServerBuilder pluginsSupplier(Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
        this.pluginsSupplier = checkNotNull(pluginsSupplier);
        return this;
    }

    Supplier<Iterable<NamedPlugin>> getPluginsSupplier() {
        return pluginsSupplier;
    }

    public StyxServerBuilder logConfigLocation(String logConfigLocation) {
        this.loggingSetUp = env -> setUpLogging(logConfigLocation);
        return this;
    }

    StyxServerBuilder logConfigLocationFromEnvironment() {
        this.loggingSetUp = env -> setUpLogging(environment.configuration().logConfigLocation());
        return this;
    }

    private static void setUpLogging(String logConfigLocation) {
        initLogging(logConfigLocation, true);
    }

    public StyxServer build() {
        loggingSetUp.accept(environment);

        if (pluginsSupplier == null) {
            pluginsSupplier = pluginsSuppliers(environment);
        }

        return new StyxServer(this);
    }
}
