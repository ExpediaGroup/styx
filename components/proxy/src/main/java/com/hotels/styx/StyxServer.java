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
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.startup.PluginStartupService;
import com.hotels.styx.startup.ProxyServerSetUp;
import com.hotels.styx.startup.ServerService;
import com.hotels.styx.startup.StyxPipelineFactory;
import com.hotels.styx.startup.StyxServerComponents;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.ServerConfigSchema.validateServerConfiguration;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.shutdownLogging;
import static com.hotels.styx.startup.CoreMetrics.registerCoreMetrics;
import static com.hotels.styx.startup.ProxyStatusNotifications.notifyProxyFailed;
import static com.hotels.styx.startup.ProxyStatusNotifications.notifyProxyStarted;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.FROM_CONFIG;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Entry point for styx proxy server.
 */
public final class StyxServer extends AbstractService {
    private static final String VALIDATE_SERVER_CONFIG_PROPERTY = "validateServerConfig";
    private static final Logger LOG = getLogger(StyxServer.class);

    static {
        // Disable resource leak detection if no system property supplied
        LOG.debug("Real -Dio.netty.leakDetectionLevel = " + getProperty("io.netty.leakDetectionLevel"));

        if (getProperty("io.netty.leakDetectionLevel") == null) {
            ResourceLeakDetector.setLevel(DISABLED);
        }

        LOG.debug("Real resource leak detection level = {}", ResourceLeakDetector.getLevel());
    }

    public static void main(String[] args) {
        try {
            StyxServer styxServer = createStyxServer(args);
            getRuntime().addShutdownHook(new Thread(() -> styxServer.stopAsync().awaitTerminated()));

            styxServer.startAsync().awaitRunning(styxServer.startupTimeoutSeconds, SECONDS);
        } catch (SchemaValidationException cause) {
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

        YamlConfiguration yamlConfiguration = new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides(System.getProperties())
                .build()
                .parse(configSource(startupConfig.configFileLocation()));

        validateConfiguration(startupConfig, yamlConfiguration);

        StyxServerComponents components = new StyxServerComponents.Builder()
                .styxConfig(new StyxConfig(startupConfig, yamlConfiguration))
                .loggingSetUp(FROM_CONFIG)
                .build();

        return new StyxServer(components, stopwatch);
    }

    private static void validateConfiguration(StartupConfig startupConfig, YamlConfiguration yamlConfiguration) {
        if (skipServerConfigValidation()) {
            LOG.warn("Server configuration validation disabled. The Styx server configuration will not be validated.");
        } else {
            validateServerConfiguration(yamlConfiguration)
                    .ifPresent(message -> {
                        LOG.info("Styx server failed to start due to configuration error.");
                        LOG.info("The configuration was sourced from " + startupConfig.configFileLocation());
                        LOG.info(message);
                        throw new SchemaValidationException(message);
                    });
            LOG.info("Configuration validated successfully.");
        }
    }

    private static boolean skipServerConfigValidation() {
        String validate = getProperty(VALIDATE_SERVER_CONFIG_PROPERTY, "no");
        return "n".equals(validate) || "no".equals(validate);
    }

    private final ConfigStore configStore;
    private final long startupTimeoutSeconds;

    private final ServiceManager serviceManager;
    private final Stopwatch stopwatch;

    private final ServerReference proxyServer = new ServerReference("Proxy server");
    private final ServerReference adminServer = new ServerReference("Admin server");

    public StyxServer(StyxServerComponents config) {
        this(config, null);
    }

    public StyxServer(StyxServerComponents components, Stopwatch stopwatch) {
        this.startupTimeoutSeconds = components.environment().configuration()
                .get("startup.timeoutSeconds", Integer.class)
                .map(timeout -> (long) timeout)
                .orElse(MAX_VALUE);

        this.stopwatch = stopwatch;

        registerCoreMetrics(components.environment().buildInfo(), components.environment().metricRegistry());

        Map<String, StyxService> servicesFromConfig = components.services();

        PluginStartupService pluginStartupService = new PluginStartupService(components);

        this.configStore = components.environment().configStore();

        StyxService adminServerService = new ServerService("adminServer", () ->
                this.adminServer.store(createAdminServer(components)));

        StyxService proxyServerService = new ServerService("proxyServer", () ->
                this.proxyServer.store(createProxyServer(components)))
                .doOnError(err -> notifyProxyFailed(configStore));

        this.serviceManager = new ServiceManager(new ArrayList<Service>() {
            {
                add(toGuavaService(pluginStartupService));
                add(toGuavaService(proxyServerService));
                add(toGuavaService(adminServerService));
                servicesFromConfig.values().stream()
                        .map(StyxServer::toGuavaService)
                        .forEach(this::add);
            }
        });
    }

    public InetSocketAddress proxyHttpAddress() {
        return proxyServer.get().httpAddress();
    }

    public InetSocketAddress proxyHttpsAddress() {
        return proxyServer.get().httpsAddress();
    }

    public InetSocketAddress adminHttpAddress() {
        return adminServer.get().httpAddress();
    }

    public InetSocketAddress adminHttpsAddress() {
        return adminServer.get().httpsAddress();
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
        newSingleThreadExecutor().submit(() -> {
            printBanner();
            this.serviceManager.addListener(new ServerStartListener(this));
            this.serviceManager.startAsync().awaitHealthy();

            notifyProxyStarted(this.configStore, proxyServer.get());

            if (stopwatch == null) {
                LOG.info("Started Styx server");
            } else {
                LOG.info("Started Styx server in {} ms", stopwatch.elapsed(MILLISECONDS));
            }
        });
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
                            if (e instanceof InterruptedException) {
                                currentThread().interrupt();
                            }

                            notifyFailed(e);
                            return null;
                        });
            }

            @Override
            protected void doStop() {
                styxService.stop()
                        .thenAccept(x -> notifyStopped())
                        .exceptionally(e -> {
                            if (e instanceof InterruptedException) {
                                currentThread().interrupt();
                            }

                            notifyFailed(e);
                            return null;
                        });
            }
        };
    }

    private static HttpServer createProxyServer(StyxServerComponents components) throws InterruptedException {
        return new ProxyServerSetUp(new StyxPipelineFactory())
                .createProxyServer(components);
    }

    private static HttpServer createAdminServer(StyxServerComponents config) {
        return new AdminServerBuilder(config.environment())
                .backendServicesRegistry((Registry<BackendService>) config.services().get("backendServiceRegistry"))
                .build();
    }

    // Add during implementation of liveness/readiness checks. This saves us from having to re-architect all the tests that rely on
    // synchronous methods like proxyHttpAddress.
    private static class ServerReference {
        private final AtomicReference<HttpServer> reference;
        private final String name;

        ServerReference(String name) {
            this.reference = new AtomicReference<>();
            this.name = requireNonNull(name);
        }

        HttpServer store(HttpServer server) {
            this.reference.set(server);
            return server;
        }

        HttpServer get() {
            HttpServer server = reference.get();

            if (server == null) {
                throw new IllegalStateException(name + " has not finished starting up");
            }

            return server;
        }
    }

    private static class ServerStartListener extends ServiceManager.Listener {
        private final StyxServer styxServer;

        ServerStartListener(StyxServer styxServer) {
            this.styxServer = styxServer;
        }

        @Override
        public void healthy() {
            styxServer.notifyStarted();
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
