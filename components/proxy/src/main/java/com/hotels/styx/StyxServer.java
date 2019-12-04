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
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpServer;
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

import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.shutdownLogging;
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

    private final HttpServer httpServer;
    private final HttpServer httpsServer;
    private final HttpServer adminServer;

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

    private final ServiceManager serviceManager;

    private final Stopwatch stopwatch;

    public StyxServer(StyxServerComponents config) {
        this(config, null);
    }

    private static HttpServer httpServer(StyxServerComponents config, ConnectorConfig connectorConfig, HttpHandler styxDataPlane) {
        return new ProxyServerBuilder(config.environment())
                .handler(styxDataPlane)
                .connectorConfig(connectorConfig)
                .build();
    }

    public StyxServer(StyxServerComponents components, Stopwatch stopwatch) {
        this.stopwatch = stopwatch;

        registerCoreMetrics(components.environment().buildInfo(), components.environment().metricRegistry());

        HttpHandler styxDataPlane = new StyxPipelineFactory(
                components.routingObjectFactoryContext(),
                components.environment(),
                components.services(),
                components.plugins(),
                components.eventLoopGroup(),
                components.nettySocketChannelClass()).create();

        ArrayList<Service> services = new ArrayList<>();

        StyxConfig styxConfig = components.environment().configuration();

        httpServer = styxConfig.proxyServerConfig()
                .httpConnectorConfig()
                .map(it -> httpServer(components, it, styxDataPlane))
                .orElse(null);

        httpsServer = styxConfig.proxyServerConfig()
                .httpsConnectorConfig()
                .map(it -> httpServer(components, it, styxDataPlane))
                .orElse(null);

        adminServer = createAdminServer(components);

        Optional.ofNullable(httpServer).ifPresent(services::add);
        Optional.ofNullable(httpsServer).ifPresent(services::add);

        services.add(adminServer);
        services.add(toGuavaService(new AbstractStyxService("Plugins manager") {
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
        }));
        services.add(toGuavaService(new ServiceProviderMonitor("Styx-Service-Monitor", components.servicesDatabase())));
        components.services().values().forEach(it -> services.add(toGuavaService(it)));

        this.serviceManager = new ServiceManager(services);
    }

    public InetSocketAddress proxyHttpAddress() {
        return Optional.ofNullable(httpServer)
                .map(HttpServer::httpAddress)
                .orElse(null);
    }

    public InetSocketAddress proxyHttpsAddress() {
        return Optional.ofNullable(httpsServer)
                .map(HttpServer::httpAddress)
                .orElse(null);
    }

    public InetSocketAddress adminHttpAddress() {
        return adminServer.httpAddress();
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
        printBanner();
        this.serviceManager.addListener(new ServerStartListener(this));
        this.serviceManager.startAsync();
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

    @Override
    protected void doStop() {
        this.serviceManager.stopAsync();
        shutdownLogging(true);
    }

    private static Service toGuavaService(StyxService styxService) {
        return new AbstractService() {
            @Override
            protected void doStart() {
                styxService.start()
                        .thenAccept(x -> notifyStarted())
                        .exceptionally(e -> {
                            notifyFailed(e);
                            return null;
                        });
            }

            @Override
            protected void doStop() {
                styxService.stop()
                        .thenAccept(x -> notifyStopped())
                        .exceptionally(e -> {
                            notifyFailed(e);
                            return null;
                        });
            }
        };
    }

    private static HttpServer createAdminServer(StyxServerComponents components) {
        Registry<BackendService> registry = (Registry<BackendService>) components.services().get("backendServiceRegistry");

        return new AdminServerBuilder(components)
                .backendServicesRegistry(registry != null ? registry : new MemoryBackedRegistry<>())
                .build();
    }

    private class ServerStartListener extends ServiceManager.Listener {
        private final StyxServer styxServer;

        ServerStartListener(StyxServer styxServer) {
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
            LOG.warn("Failed to start service={} cause={}", service, service.failureCause());
            styxServer.notifyFailed(service.failureCause());
        }

        @Override
        public void stopped() {
            LOG.warn("Stopped");
            styxServer.notifyStopped();
        }
    }

}
