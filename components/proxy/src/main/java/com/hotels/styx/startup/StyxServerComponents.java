/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.AsyncEventBus;
import com.hotels.styx.Environment;
import com.hotels.styx.StartupConfig;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.client.netty.eventloop.PlatformAwareClientEventLoopGroupFactory;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingMetadataDecorator;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.RouteRefLookup.RouteDbRefLookup;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.Version.readVersionFrom;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_FACTORIES;
import static com.hotels.styx.routing.config.Builtins.INTERCEPTOR_FACTORIES;
import static com.hotels.styx.startup.ServicesLoader.SERVICES_FROM_CONFIG;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.DO_NOT_MODIFY;
import static com.hotels.styx.startup.extensions.PluginLoadingForStartup.loadPlugins;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toList;

/**
 * Configuration required to set-up the core Styx services, such as the proxy and admin servers.
 */
public class StyxServerComponents {
    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;
    private final StyxObjectStore<RoutingObjectRecord> routeObjectStore = new StyxObjectStore<>();
    private final EventLoopGroup eventLoopGroup;
    private final Class<? extends SocketChannel> nettySocketChannelClass;
    private final RoutingObjectFactory.Context routingObjectContext;
    private final StartupConfig startupConfig;

    private static final Logger LOGGER = LoggerFactory.getLogger(StyxServerComponents.class);

    private StyxServerComponents(Builder builder) {
        StyxConfig styxConfig = requireNonNull(builder.styxConfig);

        this.startupConfig = builder.startupConfig == null ? newStartupConfigBuilder().build() : builder.startupConfig;

        this.environment = newEnvironment(styxConfig, builder.metricRegistry);
        builder.loggingSetUp.setUp(environment);

        PlatformAwareClientEventLoopGroupFactory factory = new PlatformAwareClientEventLoopGroupFactory(
                "Styx",
                environment.configuration().proxyServerConfig().clientWorkerThreadsCount());

        this.eventLoopGroup = factory.newClientWorkerEventLoopGroup();
        this.nettySocketChannelClass = factory.clientSocketChannelClass();

        // TODO In further refactoring, we will probably want this loading to happen outside of this constructor call,
        //  so that it doesn't delay the admin server from starting up
        this.plugins = builder.configuredPluginFactories == null
                ? loadPlugins(environment)
                : loadPlugins(environment, builder.configuredPluginFactories);

        this.routingObjectContext = new RoutingObjectFactory.Context(
                new RouteDbRefLookup(this.routeObjectStore),
                environment,
                routeObjectStore,
                BUILTIN_HANDLER_FACTORIES,
                plugins,
                INTERCEPTOR_FACTORIES,
                false);

        this.services = mergeServices(
                builder.servicesLoader.load(environment, routeObjectStore),
                builder.additionalServices
        );

        this.plugins.forEach(plugin -> this.environment.configStore().set("plugins." + plugin.name(), plugin));

        this.environment.configuration().get("routingObjects", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(ImmutableMap.of())
                .forEach((name, record) -> {
                    RoutingMetadataDecorator adapter = new RoutingMetadataDecorator(Builtins.build(ImmutableList.of(name), routingObjectContext, record));
                    routeObjectStore.insert(name, new RoutingObjectRecord(record.type(), ImmutableSet.copyOf(record.tags()), record.config(), adapter))
                            .ifPresent(previous -> previous.getRoutingObject().stop());
                });

        this.environment.configuration().get("providers", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(ImmutableMap.of())
                .forEach((name, record) -> {
                    LOGGER.warn("record: " + name + ": " + record);
                });
    }

    private static Map<String, StyxObjectDefinition> readComponents(JsonNode root) {
        Map<String, StyxObjectDefinition> handlers = new HashMap<>();

        root.fields().forEachRemaining(
                entry -> {
                    String name = entry.getKey();
                    StyxObjectDefinition handlerDef = new JsonNodeConfig(entry.getValue()).as(StyxObjectDefinition.class);
                    handlers.put(name, handlerDef);
                }
        );

        return handlers;
    }

    public Environment environment() {
        return environment;
    }

    public Map<String, StyxService> services() {
        return services;
    }

    public List<NamedPlugin> plugins() {
        return plugins;
    }

    public StyxObjectStore<RoutingObjectRecord> routeDatabase() {
        return this.routeObjectStore;
    }

    public RoutingObjectFactory.Context routingObjectFactoryContext() {
        return this.routingObjectContext;
    }

    public EventLoopGroup eventLoopGroup() {
        return this.eventLoopGroup;
    }

    public Class<? extends SocketChannel> nettySocketChannelClass() {
        return this.nettySocketChannelClass;
    }

    public StartupConfig startupConfig() {
        return startupConfig;
    }

    private static Environment newEnvironment(StyxConfig styxConfig, MetricRegistry metricRegistry) {
        return new Environment.Builder()
                .configuration(styxConfig)
                .metricRegistry(metricRegistry)
                .buildInfo(readBuildInfo())
                .eventBus(new AsyncEventBus("styx", newSingleThreadExecutor()))
                .build();
    }

    private static Version readBuildInfo() {
        return readVersionFrom("/version.json");
    }

    private static Map<String, StyxService> mergeServices(Map<String, StyxService> configServices, Map<String, StyxService> additionalServices) {
        if (additionalServices == null) {
            return configServices;
        }

        return new ImmutableMap.Builder<String, StyxService>()
                .putAll(configServices)
                .putAll(additionalServices)
                .build();
    }

    /**
     * CoreConfig builder.
     */
    public static final class Builder {
        private StyxConfig styxConfig;
        private LoggingSetUp loggingSetUp = DO_NOT_MODIFY;
        private List<ConfiguredPluginFactory> configuredPluginFactories;
        private ServicesLoader servicesLoader = SERVICES_FROM_CONFIG;
        private MetricRegistry metricRegistry = new CodaHaleMetricRegistry();
        private StartupConfig startupConfig;

        private final Map<String, StyxService> additionalServices = new HashMap<>();

        public Builder styxConfig(StyxConfig styxConfig) {
            this.styxConfig = requireNonNull(styxConfig);
            return this;
        }

        public Builder metricsRegistry(MetricRegistry metricRegistry) {
            this.metricRegistry = requireNonNull(metricRegistry);
            return this;
        }

        public Builder configuration(Configuration configuration) {
            return styxConfig(new StyxConfig(configuration));
        }

        public Builder loggingSetUp(LoggingSetUp loggingSetUp) {
            this.loggingSetUp = requireNonNull(loggingSetUp);
            return this;
        }

        @VisibleForTesting
        public Builder loggingSetUp(String logConfigLocation) {
            this.loggingSetUp = env -> initLogging(logConfigLocation, true);
            return this;
        }

        @VisibleForTesting
        public Builder plugins(Map<String, Plugin> plugins) {
            return pluginFactories(stubFactories(plugins));
        }

        private static List<ConfiguredPluginFactory> stubFactories(Map<String, Plugin> plugins) {
            return plugins.entrySet().stream().map(entry -> {
                String name = entry.getKey();
                Plugin plugin = entry.getValue();

                return new ConfiguredPluginFactory(name, any -> plugin);
            }).collect(toList());
        }

        public Builder pluginFactories(List<ConfiguredPluginFactory> configuredPluginFactories) {
            this.configuredPluginFactories = requireNonNull(configuredPluginFactories);
            return this;
        }

        @VisibleForTesting
        Builder services(ServicesLoader servicesLoader) {
            this.servicesLoader = requireNonNull(servicesLoader);
            return this;
        }

        @VisibleForTesting
        public Builder additionalServices(Map<String, StyxService> services) {
            this.additionalServices.putAll(services);
            return this;
        }

        public Builder startupConfig(StartupConfig startupConfig) {
            this.startupConfig = startupConfig;
            return this;
        }

        public StyxServerComponents build() {
            return new StyxServerComponents(this);
        }
    }

    /**
     * Set-up the logging.
     */
    public interface LoggingSetUp {
        LoggingSetUp DO_NOT_MODIFY = environment -> {
        };

        void setUp(Environment environment);
    }
}
