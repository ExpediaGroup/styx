/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.admin;

import com.codahale.metrics.json.MetricsModule;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.Environment;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.InetServer;
import com.hotels.styx.StartupConfig;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.admin.dashboard.DashboardData;
import com.hotels.styx.admin.dashboard.DashboardDataSupplier;
import com.hotels.styx.admin.handlers.CurrentRequestsHandler;
import com.hotels.styx.admin.handlers.IndexHandler;
import com.hotels.styx.admin.handlers.JVMMetricsHandler;
import com.hotels.styx.admin.handlers.JsonHandler;
import com.hotels.styx.admin.handlers.LoggingConfigurationHandler;
import com.hotels.styx.admin.handlers.MetricsHandler;
import com.hotels.styx.admin.handlers.OriginsHandler;
import com.hotels.styx.admin.handlers.OriginsInventoryHandler;
import com.hotels.styx.admin.handlers.PingHandler;
import com.hotels.styx.admin.handlers.PluginListHandler;
import com.hotels.styx.admin.handlers.PluginToggleHandler;
import com.hotels.styx.admin.handlers.ProviderRoutingHandler;
import com.hotels.styx.admin.handlers.RoutingObjectHandler;
import com.hotels.styx.admin.handlers.ServiceProviderHandler;
import com.hotels.styx.admin.handlers.StartupConfigHandler;
import com.hotels.styx.admin.handlers.StyxConfigurationHandler;
import com.hotels.styx.admin.handlers.ThreadsHandler;
import com.hotels.styx.admin.handlers.UptimeHandler;
import com.hotels.styx.admin.handlers.VersionTextHandler;
import com.hotels.styx.admin.tasks.OriginsCommandHandler;
import com.hotels.styx.admin.tasks.OriginsReloadCommandHandler;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.common.http.handler.HttpAggregator;
import com.hotels.styx.common.http.handler.HttpMethodFilteringHandler;
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.db.StyxObjectStore;
import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.server.AdminHttpRouter;
import com.hotels.styx.server.handlers.ClassPathResourceHandler;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import com.hotels.styx.server.track.CurrentRequestTracker;
import com.hotels.styx.startup.StyxServerComponents;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.admin.handlers.IndexHandler.Link.link;
import static com.hotels.styx.api.HttpMethod.POST;
import static com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V1;
import static com.hotels.styx.routing.config.ConfigVersionResolver.configVersion;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builder for AdminServer.
 */
public class AdminServerBuilder {
    private static final Logger LOG = getLogger(AdminServerBuilder.class);
    private static final int MEGABYTE = 1024 * 1024;

    private final Environment environment;
    private final Configuration configuration;
    private final RoutingObjectFactory.Context routingObjectFactoryContext;
    private final StyxObjectStore<RoutingObjectRecord> routeDatabase;
    private final StyxObjectStore<StyxObjectRecord<StyxService>> providerDatabase;
    private final StartupConfig startupConfig;

    private Registry<BackendService> backendServicesRegistry;

    public AdminServerBuilder(StyxServerComponents serverComponents) {
        this.environment = requireNonNull(serverComponents.environment());
        this.routeDatabase = requireNonNull(serverComponents.routeDatabase());
        this.routingObjectFactoryContext = requireNonNull(serverComponents.routingObjectFactoryContext());
        this.providerDatabase = requireNonNull(serverComponents.servicesDatabase());
        this.configuration = this.environment.configuration();
        this.startupConfig = serverComponents.startupConfig();
    }

    public AdminServerBuilder backendServicesRegistry(Registry<BackendService> backendServicesRegistry) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry);
        return this;
    }

    public InetServer build() {
        LOG.info("event bus that will be used is {}", environment.eventBus());
        StyxConfig styxConfig = environment.configuration();
        AdminServerConfig adminServerConfig = styxConfig.adminServerConfig();

        NettyExecutor executor = NettyExecutor.create("Admin-Boss", adminServerConfig.bossThreadsCount());
        NettyServerBuilder builder = NettyServerBuilder.newBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .bossExecutor(executor)
                .workerExecutor(NettyExecutor.create("Admin-Worker", adminServerConfig.workerThreadsCount()))
                .handler(adminEndpoints(styxConfig, startupConfig));

        // Currently admin server cannot be started over TLS protocol.
        // This appears to be an existing issue that needs rectifying.
        adminServerConfig.httpConnectorConfig().ifPresent(it -> builder.setProtocolConnector(new WebServerConnectorFactory().create(it)));

        return builder.build();
    }

    private HttpHandler adminEndpoints(StyxConfig styxConfig, StartupConfig startupConfig) {
        Optional<Duration> metricsCacheExpiration = styxConfig.adminServerConfig().metricsCacheExpiration();

        AdminHttpRouter httpRouter = new AdminHttpRouter();
        httpRouter.aggregate("/", new IndexHandler(indexLinkPaths(styxConfig)));
        httpRouter.aggregate("/version.txt", new VersionTextHandler(styxConfig.versionFiles(startupConfig)));
        httpRouter.aggregate("/admin", new IndexHandler(indexLinkPaths(styxConfig)));
        httpRouter.aggregate("/admin/uptime", new UptimeHandler(environment.metricRegistry()));
        httpRouter.aggregate("/admin/ping", new PingHandler());
        httpRouter.aggregate("/admin/threads", new ThreadsHandler());
        httpRouter.aggregate("/admin/current_requests", new CurrentRequestsHandler(CurrentRequestTracker.INSTANCE));
        MetricsHandler metricsHandler = new MetricsHandler(environment.metricRegistry(), metricsCacheExpiration);
        httpRouter.aggregate("/admin/metrics", metricsHandler);
        httpRouter.aggregate("/admin/metrics/", metricsHandler);
        httpRouter.aggregate("/admin/configuration", new StyxConfigurationHandler(configuration));
        httpRouter.aggregate("/admin/jvm", new JVMMetricsHandler(environment.metricRegistry(), metricsCacheExpiration));
        httpRouter.aggregate("/admin/configuration/logging", new LoggingConfigurationHandler(startupConfig.logConfigLocation()));
        httpRouter.aggregate("/admin/configuration/startup", new StartupConfigHandler(startupConfig));

        RoutingObjectHandler routingObjectHandler = new RoutingObjectHandler(routeDatabase, routingObjectFactoryContext);
        httpRouter.aggregate("/admin/routing", routingObjectHandler);
        httpRouter.aggregate("/admin/routing/", routingObjectHandler);

        ServiceProviderHandler serviceProvideHandler = new ServiceProviderHandler(providerDatabase);
        httpRouter.aggregate("/admin/service/providers", serviceProvideHandler);
        httpRouter.aggregate("/admin/service/provider/", serviceProvideHandler);

        if (configVersion(styxConfig) == ROUTING_CONFIG_V1) {
            httpRouter.aggregate("/admin/dashboard/data.json", dashboardDataHandler(styxConfig));
            httpRouter.aggregate("/admin/dashboard/", new ClassPathResourceHandler("/admin/dashboard/"));
        }

        // Replace them in the backwards compatibility mode only.
        // Remove altogether when Routing Engine is enabled:
        httpRouter.aggregate("/admin/origins/status", new OriginsInventoryHandler(environment.eventBus()));
        httpRouter.aggregate("/admin/configuration/origins", new OriginsHandler(backendServicesRegistry));
        httpRouter.aggregate("/admin/tasks/origins/reload", new HttpMethodFilteringHandler(POST, new OriginsReloadCommandHandler(backendServicesRegistry)));
        httpRouter.aggregate("/admin/tasks/origins", new HttpMethodFilteringHandler(POST, new OriginsCommandHandler(environment.eventBus())));

        httpRouter.aggregate("/admin/tasks/plugin/", new PluginToggleHandler(environment.plugins()));

        // Plugins Handler
        environment.plugins()
                .forEach(namedPlugin -> {
                    extensionEndpoints("plugins", namedPlugin.name(), namedPlugin.adminInterfaceHandlers())
                            .forEach(route -> httpRouter.stream(route.path(), route.handler()));
                });

        httpRouter.aggregate("/admin/plugins", new PluginListHandler(environment.plugins()));

        ProviderRoutingHandler providerHandler = new ProviderRoutingHandler("/admin/providers", providerDatabase);
        httpRouter.aggregate("/admin/providers", providerHandler);
        httpRouter.aggregate("/admin/providers/", providerHandler);

        return httpRouter;
    }

    private JsonHandler<DashboardData> dashboardDataHandler(StyxConfig styxConfig) {
        return new JsonHandler<>(new DashboardDataSupplier(backendServicesRegistry, environment, styxConfig),
                Optional.of(Duration.ofSeconds(10)),
                new MetricsModule(SECONDS, MILLISECONDS, false));
    }

    private static Iterable<IndexHandler.Link> indexLinkPaths(StyxConfig styxConfig) {
        ImmutableList.Builder<IndexHandler.Link> builder = ImmutableList.builder();
        builder.add(link("version.txt", "/version.txt"));
        builder.add(link("uptime", "/admin/uptime"));
        builder.add(link("Ping", "/admin/ping"));
        builder.add(link("Threads", "/admin/threads"));
        builder.add(link("Current Requests", "/admin/current_requests?withStackTrace=true"));
        builder.add(link("Metrics", "/admin/metrics?pretty"));
        builder.add(link("Configuration", "/admin/configuration?pretty"));
        builder.add(link("Log Configuration", "/admin/configuration/logging"));
        builder.add(link("Startup Configuration", "/admin/configuration/startup"));
        builder.add(link("JVM", "/admin/jvm?pretty"));
        builder.add(link("Plugins", "/admin/plugins"));
        builder.add(link("Providers", "/admin/providers"));

        if (configVersion(styxConfig) == ROUTING_CONFIG_V1) {
            builder.add(link("Dashboard", "/admin/dashboard/index.html"))
                    .add(link("Origins Status", "/admin/origins/status?pretty"))
                    .add(link("Origins Configuration", "/admin/configuration/origins?pretty"));
        }
        return builder.build()
                .stream()
                .sorted()
                .collect(toList());
    }

    private static List<Route> extensionEndpoints(String root, String name, Map<String, HttpHandler> endpoints) {
        List<AdminEndpointRoute> routes = extensionAdminEndpointRoutes(root, name, endpoints);

        List<IndexHandler.Link> endpointLinks = routes.stream()
                .map(AdminEndpointRoute::link)
                .collect(toList());

        WebServiceHandler handler = endpointLinks.isEmpty()
                ? new StaticBodyHttpHandler(HTML_UTF_8, format("This plugin (%s) does not expose any admin interfaces", name))
                : new IndexHandler(endpointLinks);

        Route indexRoute = new Route(adminPath(root, name), new HttpAggregator(MEGABYTE, handler));

        return concatenate(indexRoute, routes);
    }

    private static <T> List<T> concatenate(T item, List<? extends T> items) {
        List<T> list = new ArrayList<>(items.size() + 1);
        list.add(item);
        list.addAll(items);
        return list;
    }

    public static String adminPath(String root, String name) {
        return String.format("/admin/%s/%s", root, name);
    }

    public static String adminEndpointPath(String root, String name, String relativePath) {
        return adminPath(root, name) + "/" + dropFirstForwardSlash(relativePath);
    }

    private static String dropFirstForwardSlash(String key) {
        return key.length() > 0 && key.charAt(0) == '/' ? key.substring(1) : key;
    }


    private static List<AdminEndpointRoute> extensionAdminEndpointRoutes(String root, String name, Map<String, HttpHandler> endpoints) {
        return mapToList(endpoints, (relativePath, handler) ->
                new AdminEndpointRoute(root, name, relativePath, handler));
    }

    // allows key and value to be labelled in lambda instead of having to use Entry.getKey, Entry.getValue
    private static <K, V, T> List<T> mapToList(Map<K, V> map, BiFunction<K, V, T> function) {
        return map.entrySet().stream()
                .map(entry -> function.apply(entry.getKey(), entry.getValue()))
                .collect(toList());
    }

    private static class Route {
        private final String path;
        private final HttpHandler handler;

        Route(String path, HttpHandler handler) {
            this.path = path;
            this.handler = handler;
        }

        String path() {
            return path;
        }

        HttpHandler handler() {
            return handler;
        }
    }

    private static class AdminEndpointRoute extends Route {
        private final String root;
        private final String name;

        AdminEndpointRoute(String root, String name, String relativePath, HttpHandler handler) {
            super(adminEndpointPath(root, name, relativePath), handler);
            this.root = root;
            this.name = name;
        }

        String linkLabel() {
            String relativePath = path().substring(adminPath(root, name).length() + 1);
            return name + ": " + dropFirstForwardSlash(relativePath);
        }

        IndexHandler.Link link() {
            return IndexHandler.Link.link(linkLabel(), path());
        }
    }
}
