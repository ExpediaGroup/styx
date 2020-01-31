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
package com.hotels.styx;

import com.google.common.base.Stopwatch;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.admin.AdminServerBuilder;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.debug.RequestDebugger;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.startup.StyxServerComponents;
import io.netty.util.ResourceLeakDetector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.StyxServers.toGuavaService;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.shutdownLogging;
import static com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS;
import static com.hotels.styx.startup.CoreMetrics.registerCoreMetrics;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Entry point for styx proxy server.
 */
public final class StyxServer extends AbstractService {
    private static final Logger LOG = getLogger(StyxServer.class);

    static {
        // Disable resource leak detection if no system property supplied
        LOG.debug("Real -Dio.netty.leakDetectionLevel = " + getProperty("io.netty.leakDetectionLevel"));

        if (getProperty("io.netty.leakDetectionLevel") == null) {
            ResourceLeakDetector.setLevel(DISABLED);
        }

        LOG.debug("Real resource leak detection level = {}", ResourceLeakDetector.getLevel());
    }

    private final InetServer httpServer;
    private final InetServer httpsServer;
    private final InetServer adminServer;

    private final ServiceManager phase1Services;
    private final ServiceManager phase2Services;
    private final Stopwatch stopwatch;
    private final StyxServerComponents components;

    public static void main(String[] args) {
        try {
            StyxServer styxServer = createStyxServer(args);
            getRuntime().addShutdownHook(new Thread(() -> styxServer.stopAsync().awaitTerminated()));

            styxServer.startAsync().awaitRunning();
        } catch (SchemaValidationException cause) {
            LOG.error(cause.getMessage());
            System.exit(2);
        } catch (Throwable cause) {
            LOG.error("Error in Styx server startup.", cause);
            System.exit(1);
        }
    }

    private static StyxServer createStyxServer(String[] args) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        StartupConfig startupConfig = parseStartupConfig(args);

        LOG.info("Styx home={}", startupConfig.styxHome());
        LOG.info("Styx configFileLocation={}", startupConfig.configFileLocation());
        LOG.info("Styx logConfigLocation={}", startupConfig.logConfigLocation());

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(parseConfiguration(startupConfig))
                .startupConfig(startupConfig)
                .loggingSetUp(environment -> activateLogbackConfigurer(startupConfig))
                .build();

        return new StyxServer(components, stopwatch);
    }

    private static void activateLogbackConfigurer(StartupConfig startupConfig) {
        // the LOGBackConfigurer overrides the logger, so we can't use LoggingTestSupport in unit tests if it runs.
        if (!isUnitTestingMode()) {
            initLogging(logConfigLocation(startupConfig), true);
        }
    }

    private static boolean isUnitTestingMode() {
        return Optional.ofNullable(System.getProperty("UNIT_TESTING_MODE"))
                .filter(value -> !value.isEmpty())
                .isPresent();
    }

    private static String logConfigLocation(StartupConfig startupConfig) {
        return Paths.get(startupConfig
                .logConfigLocation()
                .url()
                .getFile())
                .toString();
    }

    @NotNull
    private static StyxConfig parseConfiguration(StartupConfig startupConfig) {
        String yaml = readYaml(startupConfig.configFileLocation());

        try {
            return StyxConfig.fromYaml(yaml);
        } catch (SchemaValidationException e) {
            String errorFormat = "Styx server failed to start due to configuration error in file [%s]: %s";

            throw new SchemaValidationException(format(errorFormat, startupConfig.configFileLocation(), e.getMessage()));
        }
    }

    private static String readYaml(Resource resource) {
        try (Reader reader = new BufferedReader(new InputStreamReader(resource.inputStream()))) {
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StyxServer(StyxServerComponents config) {
        this(config, null);
    }

    public StyxServer(StyxServerComponents components, Stopwatch stopwatch) {
        this.stopwatch = stopwatch;
        this.components = components;

        registerCoreMetrics(components.environment().buildInfo(), components.environment().metricRegistry());

        // The plugins are loaded, but not initialised. And therefore not able to accept traffic.
        // This handler is for the "old" proxy servers, that are started from proxy.connectors configuration.
        // The new `HttpServer` object (https://github.com/HotelsDotCom/styx/pull/591) doesn't use it.
        HttpHandler handlerForOldProxyServer = new StyxPipelineFactory(
                components.routingObjectFactoryContext(),
                components.environment(),
                components.services(),
                components.plugins(),
                components.clientExecutor())
                .create();

        // Startup phase 1: start plugins, control plane providers, and other services:
        ArrayList<Service> services = new ArrayList<>();
        adminServer = createAdminServer(components);
        services.add(toGuavaService(adminServer));
        services.add(toGuavaService(new PluginsManager("Sty§x-Plugins-Manager", components)));
        services.add(toGuavaService(new ServiceProviderMonitor<>("Styx-Service-Monitor", components.servicesDatabase())));
        components.services().values().forEach(it -> services.add(toGuavaService(it)));
        this.phase1Services = new ServiceManager(services);

        // Phase 2: start HTTP services;
        StyxConfig styxConfig = components.environment().configuration();
        httpServer = styxConfig.proxyServerConfig()
                .httpConnectorConfig()
                .map(it -> httpServer(components.environment(), it, handlerForOldProxyServer))
                .orElse(null);

        httpsServer = styxConfig.proxyServerConfig()
                .httpsConnectorConfig()
                .map(it -> httpServer(components.environment(), it, handlerForOldProxyServer))
                .orElse(null);

        ArrayList<Service> services2 = new ArrayList<>();

        Optional.ofNullable(httpServer).map(StyxServers::toGuavaService).ifPresent(services2::add);
        Optional.ofNullable(httpsServer).map(StyxServers::toGuavaService).ifPresent(services2::add);

        services2.add(toGuavaService(new ServiceProviderMonitor<>("Styx-Server-Monitor", components.serversDatabase())));

        this.phase2Services = new ServiceManager(services2);
    }

    public InetSocketAddress serverAddress(String name) {
        return components.serversDatabase()
                .get(name)
                .map(it -> it.component4().inetAddress())
                .orElse(null);
    }

    public InetSocketAddress proxyHttpAddress() {
        return Optional.ofNullable(httpServer)
                .map(InetServer::inetAddress)
                .orElse(null);
    }

    public InetSocketAddress proxyHttpsAddress() {
        return Optional.ofNullable(httpsServer)
                .map(InetServer::inetAddress)
                .orElse(null);
    }

    public InetSocketAddress adminHttpAddress() {
        return adminServer.inetAddress();
    }

    private static InetServer httpServer(Environment environment, ConnectorConfig connectorConfig, HttpHandler styxDataPlane) {
        CharSequence styxInfoHeaderName = environment.configuration().styxHeaderConfig().styxInfoHeaderName();
        ResponseInfoFormat responseInfoFormat = new ResponseInfoFormat(environment);

        ServerConnector proxyConnector = new ProxyConnectorFactory(
                environment.configuration().proxyServerConfig(),
                environment.metricRegistry(),
                environment.errorListener(),
                environment.configuration().get(ENCODE_UNWISECHARS).orElse(""),
                (builder, request) -> builder.header(styxInfoHeaderName, responseInfoFormat.format(request)),
                environment.configuration().get("requestTracking", Boolean.class).orElse(false),
                environment.httpMessageFormatter())
                .create(connectorConfig);

        return NettyServerBuilder.newBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .bossExecutor(NettyExecutor.create("Proxy-Boss", environment.configuration().proxyServerConfig().bossThreadsCount()))
                .workerExecutor(NettyExecutor.create("Proxy-Worker", environment.configuration().proxyServerConfig().workerThreadsCount()))
                .setProtocolConnector(proxyConnector)
                .handler(styxDataPlane)
                .build();
    }

    private static void initialisePlugins(Iterable<NamedPlugin> plugins) {
        int exceptions = 0;

        for (NamedPlugin plugin : plugins) {
            try {
                plugin.styxStarting();
            } catch (Exception e) {
                exceptions++;
                LOG.error("Error starting plugin '{}'", plugin.name(), e);
            }
        }

        if (exceptions > 0) {
            throw new RuntimeException(format("%s plugins failed to start", exceptions));
        }
    }

    private static StartupConfig parseStartupConfig(String[] args) {
        StartupConfig startupConfig = null;
        switch (args.length) {
            case 0:
                startupConfig = StartupConfig.load();
                break;
            default:
                System.err.println(format("USAGE: java %s", StyxServer.class.getName()));
                System.exit(1);
        }
        return startupConfig;
    }

    @Override
    protected void doStart() {
        RequestDebugger.add(components.environment().configuration().get("debug-request-id-prefix").orElse(null));
        printBanner();
        CompletableFuture.runAsync(() -> {
            // doStart should return quicly. Therefore offload waiting on a separate thread:
            this.phase1Services.addListener(new Phase1ServerStatusListener(this));
            this.phase1Services.startAsync().awaitHealthy();

            this.phase2Services.addListener(new Phase2ServerStatusListener(this));
            this.phase2Services.startAsync();
        });
    }

    @Override
    protected void doStop() {
        this.phase2Services.stopAsync().awaitStopped();
        this.phase1Services.stopAsync().awaitStopped();
        shutdownLogging(true);
    }

    private void printBanner() {
        try {
            try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/banner.txt"))) {
                LOG.info(format("Starting styx %n{}"), CharStreams.toString(reader));
            }
        } catch (IllegalArgumentException | IOException ignored) {
            LOG.debug("Could not display banner: ", ignored);
            LOG.info("Starting styx");
        }
    }

    private static InetServer createAdminServer(StyxServerComponents components) {
        Registry<BackendService> registry = (Registry<BackendService>) components.services().get("backendServiceRegistry");

        return new AdminServerBuilder(components)
                .backendServicesRegistry(registry != null ? registry : new MemoryBackedRegistry<>())
                .build();
    }

    private class PluginsManager extends AbstractStyxService {
        private final StyxServerComponents components;

        public PluginsManager(String name, StyxServerComponents components) {
            super(name);
            this.components = components;
        }

        @Override
        public CompletableFuture<Void> startService() {
            return CompletableFuture.runAsync(() -> initialisePlugins(components.plugins()));
        }

        @Override
        protected CompletableFuture<Void> stopService() {
            return CompletableFuture.runAsync(() -> {
                for (NamedPlugin plugin : components.plugins()) {
                    try {
                        plugin.styxStopping();
                    } catch (Exception e) {
                        LOG.error("Error stopping plugin '{}'", plugin.name(), e);
                    }
                }
            });
        }
    }

    private class Phase2ServerStatusListener extends ServiceManager.Listener {
        private final StyxServer styxServer;

        Phase2ServerStatusListener(StyxServer styxServer) {
            this.styxServer = styxServer;
        }

        @Override
        public void healthy() {
            styxServer.notifyStarted();

            if (stopwatch == null) {
                LOG.info("Started Styx server");
            } else {
                LOG.info("Started Styx server in {} ms", stopwatch.elapsed(MILLISECONDS));
            }
        }

        @Override
        public void failure(Service service) {
            LOG.error("Failed to start service={} cause={}", service, service.failureCause());
            styxServer.notifyFailed(service.failureCause());
        }

        @Override
        public void stopped() {
            LOG.info("Stopped phase 2 services");
        }
    }

    private class Phase1ServerStatusListener extends ServiceManager.Listener {
        private final StyxServer styxServer;

        Phase1ServerStatusListener(StyxServer styxServer) {
            this.styxServer = styxServer;
        }

        @Override
        public void healthy() {
            LOG.info("Started phase 1 services");
        }

        @Override
        public void failure(Service service) {
            LOG.error("Failed to start service={} cause={}", service, service.failureCause());
            styxServer.notifyFailed(service.failureCause());
        }

        @Override
        public void stopped() {
            LOG.info("Stopped phase 1 services.");
            styxServer.notifyStopped();
        }
    }

}
