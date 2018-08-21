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
package com.hotels.styx;

import com.google.common.base.Stopwatch;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.api.extension.service.spi.StyxService;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.startup.ProxyServerSetUp;
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

import static com.hotels.styx.ServerConfigSchema.validateServerConfiguration;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.shutdownLogging;
import static com.hotels.styx.startup.AdminServerSetUp.createAdminServer;
import static com.hotels.styx.startup.CoreMetrics.registerCoreMetrics;
import static com.hotels.styx.startup.StyxServerComponents.LoggingSetUp.FROM_CONFIG;
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

            styxServer.startAsync().awaitRunning();
        } catch (SchemaValidationException cause) {
            System.exit(2);
        } catch (Throwable cause) {
            LOG.error("Error in Styx server startup.", cause);
            System.exit(1);
        }
    }

    private static StyxServer createStyxServer(String[] args) {
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

        return new StyxServer(components);
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

    private final HttpServer proxyServer;
    private final HttpServer adminServer;

    private final ServiceManager serviceManager;

    public StyxServer(StyxServerComponents config) {
        registerCoreMetrics(config.environment().buildInfo(), config.environment().metricRegistry());

        Map<String, StyxService> servicesFromConfig = config.services();

        ProxyServerSetUp proxyServerSetUp = new ProxyServerSetUp(new StyxPipelineFactory());

        this.proxyServer = proxyServerSetUp.createProxyServer(config);
        this.adminServer = createAdminServer(config);

        this.serviceManager = new ServiceManager(new ArrayList<Service>() {
            {
                add(proxyServer);
                add(adminServer);
                servicesFromConfig.entrySet().stream()
                        .map(Map.Entry::getValue)
                        .map(StyxServer::toGuavaService)
                        .forEach(this::add);
            }
        });
    }

    public InetSocketAddress proxyHttpAddress() {
        return proxyServer.httpAddress();
    }

    public InetSocketAddress proxyHttpsAddress() {
        return proxyServer.httpsAddress();
    }

    public InetSocketAddress adminHttpAddress() {
        return adminServer.httpAddress();
    }

    public InetSocketAddress adminHttpsAddress() {
        return adminServer.httpsAddress();
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
        Stopwatch stopwatch = Stopwatch.createStarted();
        printBanner();
        this.serviceManager.addListener(new ServerStartListener(this));
        this.serviceManager.startAsync().awaitHealthy();
        LOG.info("Started styx server in {}ms", stopwatch.elapsed(MILLISECONDS));
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
