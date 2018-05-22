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
import com.hotels.styx.admin.handlers.HealthCheckHandler;
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
import com.hotels.styx.admin.handlers.ProxyStatusHandler;
import com.hotels.styx.admin.handlers.StartupConfigHandler;
import com.hotels.styx.admin.handlers.StatusHandler;
import com.hotels.styx.admin.handlers.StyxConfigurationHandler;
import com.hotels.styx.admin.handlers.ThreadsHandler;
import com.hotels.styx.admin.handlers.VersionTextHandler;
import com.hotels.styx.admin.tasks.OriginsCommandHandler;
import com.hotels.styx.admin.tasks.OriginsReloadCommandHandler;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.http.handlers.HttpMethodFilteringHandler;
import com.hotels.styx.api.http.handlers.StaticBodyHttpHandler;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.spi.Registry;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.handlers.ClassPathResourceHandler;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.admin.handlers.IndexHandler.Link.link;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
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

    private Registry<BackendService> backendServicesRegistry;

    public AdminServerBuilder(Environment environment) {
        this.environment = environment;
        this.configuration = environment.configuration();
    }

    public AdminServerBuilder backendServicesRegistry(Registry<BackendService> backendServicesRegistry) {
        this.backendServicesRegistry = checkNotNull(backendServicesRegistry);
        return this;
    }

    public HttpServer build() {
        StyxConfig styxConfig = environment.styxConfig();
        HttpHandler healthCheckHandler = new HealthCheckHandler(environment.healthCheckRegistry(), newSingleThreadExecutor());

        Optional<Duration> metricsCacheExpiration = styxConfig.adminServerConfig().metricsCacheExpiration();
        AdminServerConfig adminServerConfig = styxConfig.adminServerConfig();

        Iterable<IndexHandler.Link> links = indexLinkPaths();

        RouterBuilder httpRouter = new RouterBuilder()
                .add("/", new IndexHandler(links))
                .add("/version.txt", new VersionTextHandler(styxConfig.versionFiles()))
                .add("/admin", new IndexHandler(links))
                .add("/admin/status", new StatusHandler(healthCheckHandler))
                .add("/admin/ping", new PingHandler())
                .add("/admin/threads", new ThreadsHandler())
                .add("/admin/metrics", new MetricsHandler(environment.metricRegistry(), metricsCacheExpiration))
                .add("/admin/healthcheck", healthCheckHandler)
                .add("/admin/configuration", new StyxConfigurationHandler(staticConfiguration()))
                .add("/admin/configuration/origins", new OriginsHandler(backendServicesRegistry))
                .add("/admin/jvm", new JVMMetricsHandler(environment.metricRegistry(), metricsCacheExpiration))
                .add("/admin/origins/status", new OriginsInventoryHandler(environment.eventBus()))
                .add("/admin/configuration/logging", new LoggingConfigurationHandler(styxConfig.startupConfig().logConfigLocation()))
                .add("/admin/configuration/startup", new StartupConfigHandler(styxConfig.startupConfig()))
                .add("/admin/styx/proxy/status", new ProxyStatusHandler(environment.configStore()))

                // Dashboard
                .add("/admin/dashboard/data.json", dashboardDataHandler(styxConfig))
                .add("/admin/dashboard/", new ClassPathResourceHandler("/admin/dashboard/"))

                // Tasks
                .add("/admin/tasks/origins/reload", new HttpMethodFilteringHandler(POST, new OriginsReloadCommandHandler(backendServicesRegistry)))
                .add("/admin/tasks/origins", new HttpMethodFilteringHandler(POST, new OriginsCommandHandler(environment.eventBus())))
                ;

        environment.configStore().watch("plugins", List.class)
                .map(list -> (List<NamedPlugin>) list)
                .subscribe(plugins -> {
                    routesForPlugins(plugins).forEach(route -> httpRouter.add(route.path(), route.handler()));
                    httpRouter.add("/admin/tasks/plugin/", new PluginToggleHandler(plugins));
                    httpRouter.add("/admin/plugins", new PluginListHandler(plugins));
                });

        verifyThatLinksInIndexMapToRealEndpoints(links, httpRouter.paths);

        return new NettyServerBuilderSpec("admin", environment.serverEnvironment(), new WebServerConnectorFactory())
                .toNettyServerBuilder(adminServerConfig)
                .httpHandler(() -> httpRouter.router)
                .configStore(environment.configStore())
                .build();
    }

    private static final class RouterBuilder {
        private final StandardHttpRouter router = new StandardHttpRouter();
        private final Set<String> paths = new HashSet<>();

        public RouterBuilder add(String path, HttpHandler2 handler) {
            this.router.add(path, handler);
            this.paths.add(path);
            return this;
        }
    }

    private static void verifyThatLinksInIndexMapToRealEndpoints(Iterable<IndexHandler.Link> links, Set<String> paths) {
        for (IndexHandler.Link link : links) {
            String path = removeQueryString(link.path());

            // easiest to just make a hardcoded exception-to-the-rule here until we decide to refactor this class fully
            boolean skip = "/admin/dashboard/index.html".equals(path) || "/admin/plugins".equals(path);

            if (!skip && !paths.contains(path)) {
                throw new IllegalArgumentException(format(
                        "%s links to %s, but that path does not exist in router. Valid paths are %s%n",
                        link.label(),
                        link.path(),
                        paths));
            }
        }
    }

    private static String removeQueryString(String path) {
        int index = path.indexOf('?');

        return index == -1 ? path : path.substring(0, index);
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
                link("Status", "/admin/status"),
                link("Ping", "/admin/ping"),
                link("Proxy Status", "/admin/styx/proxy/status"),
                link("Threads", "/admin/threads"),
                link("Metrics", "/admin/metrics?pretty"),
                link("Health Check", "/admin/healthcheck"),
                link("Configuration", "/admin/configuration?pretty"),
                link("Log Configuration", "/admin/configuration/logging"),
                link("Origins Configuration", "/admin/configuration/origins?pretty"),
                link("Startup Configuration", "/admin/configuration/startup"),
                link("JVM", "/admin/jvm?pretty"),
                link("Origins Status", "/admin/origins/status?pretty"),
                link("Dashboard", "/admin/dashboard/index.html"),
                link("Plugins", "/admin/plugins"));
    }

    private static List<Route> routesForPlugins(Iterable<NamedPlugin> plugins) {
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
