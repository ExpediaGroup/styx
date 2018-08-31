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
package com.hotels.styx.admin;

import com.codahale.metrics.json.MetricsModule;
import com.google.common.collect.ImmutableSortedSet;
import com.hotels.styx.AggregatedConfiguration;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.admin.dashboard.DashboardData;
import com.hotels.styx.admin.dashboard.DashboardDataSupplier;
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
import com.hotels.styx.admin.handlers.StartupConfigHandler;
import com.hotels.styx.admin.handlers.StyxConfigurationHandler;
import com.hotels.styx.admin.handlers.ThreadsHandler;
import com.hotels.styx.admin.handlers.VersionTextHandler;
import com.hotels.styx.admin.tasks.OriginsCommandHandler;
import com.hotels.styx.admin.tasks.OriginsReloadCommandHandler;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.common.http.handler.HttpMethodFilteringHandler;
import com.hotels.styx.common.http.handler.StaticBodyHttpHandler;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.handlers.ClassPathResourceHandler;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
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
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Builder for AdminServer.
 */
public class AdminServerBuilder {
    private static final Logger LOG = getLogger(AdminServerBuilder.class);

    private final Environment environment;
    private final Configuration configuration;

    private Iterable<NamedPlugin> plugins;
    private Registry<BackendService> backendServicesRegistry;

    public AdminServerBuilder(Environment environment) {
        this.environment = environment;
        this.configuration = environment.configuration();
    }

    public AdminServerBuilder plugins(Iterable<NamedPlugin> plugins) {
        this.plugins = requireNonNull(plugins);
        return this;
    }

    public AdminServerBuilder backendServicesRegistry(Registry<BackendService> backendServicesRegistry) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry);
        return this;
    }

    public HttpServer build() {
        LOG.info("event bus that will be used is {}", environment.eventBus());
        StyxConfig styxConfig = environment.styxConfig();

        Optional<Duration> metricsCacheExpiration = styxConfig.adminServerConfig().metricsCacheExpiration();
        AdminServerConfig adminServerConfig = styxConfig.adminServerConfig();

        StandardHttpRouter httpRouter = new StandardHttpRouter();
        httpRouter.add("/", new IndexHandler(indexLinkPaths()));
        httpRouter.add("/version.txt", new VersionTextHandler(styxConfig.versionFiles()));
        httpRouter.add("/admin", new IndexHandler(indexLinkPaths()));
        httpRouter.add("/admin/ping", new PingHandler());
        httpRouter.add("/admin/threads", new ThreadsHandler());
        MetricsHandler metricsHandler = new MetricsHandler(environment.metricRegistry(), metricsCacheExpiration);
        httpRouter.add("/admin/metrics", metricsHandler);
        httpRouter.add("/admin/metrics/", metricsHandler);
        httpRouter.add("/admin/configuration", new StyxConfigurationHandler(staticConfiguration()));
        httpRouter.add("/admin/configuration/origins", new OriginsHandler(backendServicesRegistry));
        httpRouter.add("/admin/jvm", new JVMMetricsHandler(environment.metricRegistry(), metricsCacheExpiration));
        httpRouter.add("/admin/origins/status", new OriginsInventoryHandler(environment.eventBus()));
        httpRouter.add("/admin/configuration/logging", new LoggingConfigurationHandler(styxConfig.startupConfig().logConfigLocation()));
        httpRouter.add("/admin/configuration/startup", new StartupConfigHandler(styxConfig.startupConfig()));

        // Dashboard
        httpRouter.add("/admin/dashboard/data.json", dashboardDataHandler(styxConfig));
        httpRouter.add("/admin/dashboard/", new ClassPathResourceHandler("/admin/dashboard/"));

        // Tasks
        httpRouter.add("/admin/tasks/origins/reload", new HttpMethodFilteringHandler(POST, new OriginsReloadCommandHandler(backendServicesRegistry)));
        httpRouter.add("/admin/tasks/origins", new HttpMethodFilteringHandler(POST, new OriginsCommandHandler(environment.eventBus())));
        httpRouter.add("/admin/tasks/plugin/", new PluginToggleHandler(plugins));

        // Plugins Handler
        routesForPlugins().forEach(route -> httpRouter.add(route.path(), route.handler()));

        httpRouter.add("/admin/plugins", new PluginListHandler(plugins));

        return new NettyServerBuilderSpec("Admin", environment.serverEnvironment(), new WebServerConnectorFactory())
                .toNettyServerBuilder(adminServerConfig)
                .httpHandler(httpRouter)
                .build();
    }

    private JsonHandler<DashboardData> dashboardDataHandler(StyxConfig styxConfig) {
        return new JsonHandler<>(new DashboardDataSupplier(backendServicesRegistry, environment, styxConfig),
                Optional.of(Duration.ofSeconds(10)),
                new MetricsModule(SECONDS, MILLISECONDS, false));
    }

    private Configuration staticConfiguration() {
        return configuration instanceof AggregatedConfiguration
                ? ((AggregatedConfiguration) configuration).styxConfig()
                : configuration;
    }

    private static Iterable<IndexHandler.Link> indexLinkPaths() {
        return ImmutableSortedSet.of(
                link("version.txt", "/version.txt"),
                link("Ping", "/admin/ping"),
                link("Threads", "/admin/threads"),
                link("Metrics", "/admin/metrics?pretty"),
                link("Configuration", "/admin/configuration?pretty"),
                link("Log Configuration", "/admin/configuration/logging"),
                link("Origins Configuration", "/admin/configuration/origins?pretty"),
                link("Startup Configuration", "/admin/configuration/startup"),
                link("JVM", "/admin/jvm?pretty"),
                link("Origins Status", "/admin/origins/status?pretty"),
                link("Dashboard", "/admin/dashboard/index.html"),
                link("Plugins", "/admin/plugins"));
    }

    private List<Route> routesForPlugins() {
        return stream(plugins.spliterator(), true)
                .flatMap(namedPlugin -> routesForPlugin(namedPlugin).stream())
                .collect(toList());
    }

    private static List<Route> routesForPlugin(NamedPlugin namedPlugin) {
        List<PluginAdminEndpointRoute> routes = pluginAdminEndpointRoutes(namedPlugin);

        List<IndexHandler.Link> endpointLinks = routes.stream()
                .map(PluginAdminEndpointRoute::link)
                .collect(toList());

        HttpHandler handler = endpointLinks.isEmpty()
                ? new StaticBodyHttpHandler(HTML_UTF_8, format("This plugin (%s) does not expose any admin interfaces", namedPlugin.name()))
                : new IndexHandler(endpointLinks);

        Route indexRoute = new Route(pluginPath(namedPlugin), handler);

        return concatenate(indexRoute, routes);
    }

    private static <T> List<T> concatenate(T item, List<? extends T> items) {
        List<T> list = new ArrayList<>(items.size() + 1);
        list.add(item);
        list.addAll(items);
        return list;
    }

    private static String pluginPath(NamedPlugin namedPlugin) {
        return "/admin/plugins/" + namedPlugin.name();
    }

    private static List<PluginAdminEndpointRoute> pluginAdminEndpointRoutes(NamedPlugin namedPlugin) {
        Map<String, HttpHandler> adminInterfaceHandlers = namedPlugin.adminInterfaceHandlers();

        return mapToList(adminInterfaceHandlers, (relativePath, handler) ->
                new PluginAdminEndpointRoute(namedPlugin, relativePath, handler));
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

    private static class PluginAdminEndpointRoute extends Route {
        private final NamedPlugin namedPlugin;

        PluginAdminEndpointRoute(NamedPlugin namedPlugin, String relativePath, HttpHandler handler) {
            super(pluginAdminEndpointPath(namedPlugin, relativePath), handler);

            this.namedPlugin = namedPlugin;
        }

        static String pluginAdminEndpointPath(NamedPlugin namedPlugin, String relativePath) {
            return pluginPath(namedPlugin) + "/" + dropFirstForwardSlash(relativePath);
        }

        static String dropFirstForwardSlash(String key) {
            return key.charAt(0) == '/' ? key.substring(1) : key;
        }

        String linkLabel() {
            String relativePath = path().substring(pluginPath(namedPlugin).length() + 1);

            return namedPlugin.name() + ": " + dropFirstForwardSlash(relativePath);
        }

        IndexHandler.Link link() {
            return IndexHandler.Link.link(linkLabel(), path());
        }
    }
}
