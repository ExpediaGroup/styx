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
package com.hotels.styx.testapi;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.RegistryServiceAdapter;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.startup.StyxServerComponents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static com.hotels.styx.testapi.ssl.SslTesting.acceptAllSslRequests;
import static java.util.stream.Collectors.toSet;

/**
 * A Styx server that can be started up programmatically in test code.
 * <p>
 * <ul>
 * <li>Primarily intended for use in the testing of plugins.</li>
 * <li>The server provides both an HTTP and HTTPS endpoint for the Styx proxy as well as an HTTP endpoint for the admin interface.</li>
 * <li>The server can be configured to route to one or more backend services, each having one or more origins</li>
 * <li>Please note that using this server causes the JVM to accept all SSL certificates, so it should never be used in production code.</li>
 * </ul>
 */
public final class StyxServer {
    private final com.hotels.styx.StyxServer server;
    private final MetricRegistry metricRegistry;

    private StyxServer(Builder builder) {
        acceptAllSslRequests();

        MemoryBackedRegistry<com.hotels.styx.api.service.BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();

        StyxServerComponents config = new StyxServerComponents.Builder()
                .styxConfig(styxConfig())
                .plugins(builder.plugins)
                .additionalServices(ImmutableMap.of("backendServiceRegistry", new RegistryServiceAdapter(backendServicesRegistry)))
                .build();

        metricRegistry = config.environment().metricRegistry();

        this.server = new com.hotels.styx.StyxServer(config);

        builder.routes.forEach((path, backendService) ->
                backendServicesRegistry.add(backendService));
    }

    private StyxServer start() {
        server.startAsync().awaitRunning();
        return this;
    }

    private static StyxConfig styxConfig() {
        return new StyxConfig(new MapBackedConfiguration()
                .set("proxy", proxyServerConfig())
                .set("admin", adminServerConfig()));
    }

    private static AdminServerConfig adminServerConfig() {
        return new AdminServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(0))
                .build();
    }

    private static ProxyServerConfig proxyServerConfig() {
        return new ProxyServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(0))
                .setHttpsConnector(new HttpsConnectorConfig.Builder().port(0).build())
                .build();
    }

    /**
     * Stops the test server.
     */
    public void stop() {
        server.stopAsync().awaitTerminated();
    }

    /**
     * The HTTP port of the admin interface.
     *
     * @return the HTTP port of the admin interface
     */
    public int adminPort() {
        return server.adminHttpAddress().getPort();
    }

    /**
     * The HTTP port of the proxy.
     *
     * @return the HTTP port of the proxy
     */
    public int proxyHttpPort() {
        return server.proxyHttpAddress().getPort();
    }

    /**
     * The HTTPS port of the proxy.
     *
     * @return the HTTPS port of the proxy
     */
    public int proxyHttpsPort() {
        return server.proxyHttpsAddress().getPort();
    }

    /**
     * Provides the metric registry.
     *
     * @return metric registry
     */
    public MetricRegistry metrics() {
        return metricRegistry;
    }

    /**
     * A builder for constructing instances of {@link StyxServer}.
     */
    public static final class Builder {
        private final Map<String, com.hotels.styx.api.service.BackendService> routes = new HashMap<>();
        private final List<NamedPlugin> plugins = new ArrayList<>();

        /**
         * Adds a plugin to the server.
         *
         * @param name   name of plugin
         * @param plugin plugin
         * @return this builder
         */
        public Builder addPlugin(String name, Plugin plugin) {
            plugins.add(namedPlugin(name, plugin));
            return this;
        }

        /**
         * Routes to a fully configured backend service.
         *
         * @param pathPrefix     path to backend service
         * @param backendService backend service
         * @return this builder
         */
        public Builder addRoute(String pathPrefix, BackendService backendService) {
            routes.put(pathPrefix, backendService.createBackendService(pathPrefix));
            return this;
        }

        /**
         * Routes to a backend service created from an array of origins.
         *
         * @param pathPrefix path to backend service
         * @param origins    origins
         * @return this builder
         */
        public Builder addRoute(String pathPrefix, Origin... origins) {
            return addRoute(pathPrefix, ImmutableSet.copyOf(origins));
        }

        /**
         * Routes to a backend service using origins created from localhost and an array of ports.
         *
         * @param pathPrefix  path to backend service
         * @param originPorts origin ports
         * @return this builder
         */
        public Builder addRoute(String pathPrefix, int... originPorts) {
            return addRoute(pathPrefix, Arrays.stream(originPorts).mapToObj(Origins::origin).collect(toSet()));
        }

        private Builder addRoute(String pathPrefix, Set<Origin> origins) {
            return addRoute(pathPrefix, new BackendService().addOrigins(origins));
        }

        /**
         * Creates and starts the test server.
         *
         * @return new test server
         */
        public StyxServer start() {
            return new StyxServer(this).start();
        }
    }
}
