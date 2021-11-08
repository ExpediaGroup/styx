/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import com.google.common.eventbus.AsyncEventBus;
import com.hotels.styx.Environment;
import com.hotels.styx.InetServer;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.StartupConfig;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.Version;
import com.hotels.styx.api.MeterRegistry;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.common.format.SanitisedHttpHeaderFormatter;
import com.hotels.styx.common.format.SanitisedHttpMessageFormatter;
import com.hotels.styx.executors.NettyExecutorConfig;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.proxy.plugin.InstrumentedPlugin;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.routing.handlers.RouteRefLookup.RouteDbRefLookup;
import com.hotels.styx.startup.extensions.ConfiguredPluginFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.Version.readVersionFrom;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_EXECUTOR_FACTORIES;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_FACTORIES;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_SERVER_FACTORIES;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_SERVICE_PROVIDER_FACTORIES;
import static com.hotels.styx.routing.config.Builtins.INTERCEPTOR_FACTORIES;
import static com.hotels.styx.startup.ServicesLoader.SERVICES_FROM_CONFIG;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.DO_NOT_MODIFY;
import static com.hotels.styx.startup.extensions.PluginLoadingForStartup.loadPlugins;
import static com.hotels.styx.javaconvenience.UtilKt.merge;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration required to set-up the core Styx services, such as the proxy and admin servers.
 */
public class StyxServerComponents {
    private static final Logger LOGGER = getLogger(StyxServerComponents.class);
    private static final String NETTY_EXECUTOR = "NettyExecutor";
    private static final String GLOBAL_SERVER_BOSS_NAME = "StyxHttpServer-Global-Boss";
    private static final String GLOBAL_SERVER_WORKER_NAME = "StyxHttpServer-Global-Worker";
    private static final String GLOBAL_CLIENT_WORKER_NAME = "Styx-Client-Global-Worker";

    private final Environment environment;
    private final Map<String, StyxService> services;
    private final List<NamedPlugin> plugins;
    private final StyxObjectStore<RoutingObjectRecord> routeObjectStore = new StyxObjectStore<>();
    private final StyxObjectStore<StyxObjectRecord<StyxService>> providerObjectStore = new StyxObjectStore<>();
    private final StyxObjectStore<StyxObjectRecord<InetServer>> serverObjectStore = new StyxObjectStore<>();
    private final StyxObjectStore<StyxObjectRecord<NettyExecutor>> executorObjectStore = new StyxObjectStore<>();
    private final RoutingObjectFactory.Context routingObjectContext;
    private final StartupConfig startupConfig;
    private final NettyExecutor executor;
    private final boolean showBanner;

    // CHECKSTYLE:OFF
    private StyxServerComponents(Builder builder) {
        StyxConfig styxConfig = requireNonNull(builder.styxConfig);

        this.startupConfig = builder.startupConfig == null ? newStartupConfigBuilder().build() : builder.startupConfig;

        Map<String, RoutingObjectFactory> routingObjectFactories = merge(BUILTIN_HANDLER_FACTORIES, builder.additionalRoutingObjectFactories);

        this.environment = newEnvironment(styxConfig, builder.registry);
        builder.loggingSetUp.setUp(environment);

        this.executor = NettyExecutor.create("Styx-Client-Worker", environment.configuration().proxyServerConfig().clientWorkerThreadsCount());

        // Overwrite any existing or user-supplied values:
        executorObjectStore.insert(GLOBAL_SERVER_BOSS_NAME, new StyxObjectRecord<>(
                NETTY_EXECUTOR,
                Set.of("StyxInternal"),
                new NettyExecutorConfig(0, GLOBAL_SERVER_BOSS_NAME).asJsonNode(),
                NettyExecutor.create(GLOBAL_SERVER_BOSS_NAME, 0)));

        // Overwrite any existing or user-supplied values:
        executorObjectStore.insert(GLOBAL_SERVER_WORKER_NAME,
                new StyxObjectRecord<>(
                        NETTY_EXECUTOR,
                        Set.of("StyxInternal"),
                        new NettyExecutorConfig(0, GLOBAL_SERVER_WORKER_NAME).asJsonNode(),
                        NettyExecutor.create(GLOBAL_SERVER_WORKER_NAME, 0)));

        // Overwrite any existing or user-supplied values:
        executorObjectStore.insert(GLOBAL_CLIENT_WORKER_NAME,
                new StyxObjectRecord<>(
                        NETTY_EXECUTOR,
                        Set.of("StyxInternal"),
                        new NettyExecutorConfig(0, GLOBAL_CLIENT_WORKER_NAME).asJsonNode(),
                        NettyExecutor.create(GLOBAL_CLIENT_WORKER_NAME, 0)));

        this.environment.configuration().get("executors", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(Map.of())
                .forEach((name, definition) -> {
                    LOGGER.warn("Loading styx server: " + name + ": " + definition);
                    NettyExecutor executor = Builtins.buildExecutor(name, definition, BUILTIN_EXECUTOR_FACTORIES);
                    StyxObjectRecord<NettyExecutor> record = new StyxObjectRecord<>(definition.type(), Set.copyOf(definition.tags()), definition.config(), executor);
                    executorObjectStore.insert(name, record);
                });

        this.services = mergeServices(
                builder.servicesLoader.load(environment, routeObjectStore),
                builder.additionalServices
        );

        // TODO In further refactoring, we will probably want this loading to happen outside of this constructor call,
        //  so that it doesn't delay the admin server from starting up
        this.plugins = (builder.configuredPluginFactories.isEmpty()
                ? loadPlugins(environment)
                : loadPlugins(environment, builder.configuredPluginFactories)).stream().map(
                it -> new InstrumentedPlugin(it, environment)
        ).collect(toList());

        this.plugins.forEach(plugin -> this.environment.plugins().add(plugin));

        this.routingObjectContext = new RoutingObjectFactory.Context(
                new RouteDbRefLookup(this.routeObjectStore),
                environment,
                routeObjectStore,
                routingObjectFactories,
                plugins,
                INTERCEPTOR_FACTORIES,
                false,
                executorObjectStore);

        this.environment.configuration().get("routingObjects", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(Map.of())
                .forEach((name, definition) -> {
                    routeObjectStore.insert(name, RoutingObjectRecord.Companion.create(
                            definition.type(),
                            Set.copyOf(definition.tags()),
                            definition.config(),
                            Builtins.build(List.of(name), routingObjectContext, definition))
                    ).ifPresent(previous -> previous.getRoutingObject().stop());
                });

        this.environment.configuration().get("providers", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(Map.of())
                .forEach((name, definition) -> {
                    LOGGER.warn("Loading provider: " + name + ": " + definition);
                    StyxService provider = Builtins.build(name, definition, providerObjectStore, BUILTIN_SERVICE_PROVIDER_FACTORIES, routingObjectContext);
                    StyxObjectRecord<StyxService> record = new StyxObjectRecord<>(definition.type(), Set.copyOf(definition.tags()), definition.config(), provider);
                    providerObjectStore.insert(name, record);
                });

        this.environment.configuration().get("servers", JsonNode.class)
                .map(StyxServerComponents::readComponents)
                .orElse(Map.of())
                .forEach((name, definition) -> {
                    LOGGER.warn("Loading styx server: " + name + ": " + definition);
                    InetServer provider = Builtins.buildServer(name, definition, serverObjectStore, BUILTIN_SERVER_FACTORIES, routingObjectContext);
                    StyxObjectRecord<InetServer> record = new StyxObjectRecord<>(definition.type(), Set.copyOf(definition.tags()), definition.config(), provider);
                    serverObjectStore.insert(name, record);
                });

        this.showBanner = builder.showBanner;
    }
    // CHECKSTYLE:ON

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

    public boolean showBanner() {
        return showBanner;
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

    public StyxObjectStore<StyxObjectRecord<StyxService>> servicesDatabase() {
        return this.providerObjectStore;
    }

    public StyxObjectStore<StyxObjectRecord<NettyExecutor>> executors() {
        return this.executorObjectStore;
    }

    public StyxObjectStore<StyxObjectRecord<InetServer>> serversDatabase() {
        return this.serverObjectStore;
    }

    public RoutingObjectFactory.Context routingObjectFactoryContext() {
        return this.routingObjectContext;
    }


    public NettyExecutor clientExecutor() {
        return this.executor;
    }

    public StartupConfig startupConfig() {
        return startupConfig;
    }

    private static Environment newEnvironment(StyxConfig config, MeterRegistry registry) {

        SanitisedHttpHeaderFormatter headerFormatter = new SanitisedHttpHeaderFormatter(
                config.get("request-logging.hideHeaders", List.class).orElse(emptyList()),
                config.get("request-logging.hideCookies", List.class).orElse(emptyList()));

        SanitisedHttpMessageFormatter sanitisedHttpMessageFormatter = new SanitisedHttpMessageFormatter(headerFormatter);

        return new Environment.Builder()
                .configuration(config)
                .registry(registry)
                .buildInfo(readBuildInfo())
                .eventBus(new AsyncEventBus("styx", newSingleThreadExecutor()))
                .httpMessageFormatter(sanitisedHttpMessageFormatter)
                .build();
    }

    private static Version readBuildInfo() {
        return readVersionFrom("/version.json");
    }

    private static Map<String, StyxService> mergeServices(Map<String, StyxService> configServices, Map<String, StyxService> additionalServices) {
        if (additionalServices == null) {
            return configServices;
        }

        return merge(configServices, additionalServices);
    }

    /**
     * CoreConfig builder.
     */
    public static final class Builder {
        private StyxConfig styxConfig;
        private LoggingSetUp loggingSetUp = DO_NOT_MODIFY;
        private List<ConfiguredPluginFactory> configuredPluginFactories = List.of();
        private ServicesLoader servicesLoader = SERVICES_FROM_CONFIG;
        private MeterRegistry registry;
        private StartupConfig startupConfig;
        private boolean showBanner;

        private final Map<String, RoutingObjectFactory> additionalRoutingObjectFactories = new HashMap<>();
        private final Map<String, StyxService> additionalServices = new HashMap<>();

        public Builder styxConfig(StyxConfig styxConfig) {
            this.styxConfig = requireNonNull(styxConfig);
            return this;
        }

        public Builder registry(MeterRegistry registry) {
            this.registry = requireNonNull(registry);
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

        @VisibleForTesting
        public Builder additionalRoutingObjects(Map<String, RoutingObjectFactory> additionalRoutingObjectFactories) {
            this.additionalRoutingObjectFactories.putAll(additionalRoutingObjectFactories);
            return this;
        }

        public Builder showBanner(boolean showBanner) {
            this.showBanner = showBanner;
            return this;
        }

        public StyxServerComponents build() {
            if (registry == null) {
                throw new IllegalStateException("Meter registry must be specified");
            }
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
