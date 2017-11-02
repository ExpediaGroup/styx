/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.admin.AdminServerBuilder;
import com.hotels.styx.api.HttpHandler2;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.metrics.reporting.sets.NettyAllocatorMetricSet;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.proxy.interceptors.ConfigurationContextResolverInterceptor;
import com.hotels.styx.proxy.interceptors.HopByHopHeadersRemovingInterceptor;
import com.hotels.styx.proxy.interceptors.HttpMessageLoggingInterceptor;
import com.hotels.styx.proxy.interceptors.RequestEnrichingInterceptor;
import com.hotels.styx.proxy.interceptors.UnexpectedRequestContentLengthRemover;
import com.hotels.styx.proxy.interceptors.ViaHeaderAppendingInterceptor;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.routing.HttpPipelineFactory;
import com.hotels.styx.routing.StaticPipelineFactory;
import com.hotels.styx.routing.UserConfiguredPipelineFactory;
import com.hotels.styx.routing.config.ConfigVersionResolver;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.server.HttpServer;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static com.hotels.styx.api.configuration.ConfigurationContextResolver.EMPTY_CONFIGURATION_CONTEXT_RESOLVER;
import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.shutdownLogging;
import static com.hotels.styx.routing.config.ConfigVersionResolver.Version.ROUTING_CONFIG_V1;
import static com.hotels.styx.serviceproviders.ServiceProvision.loadServices;
import static io.netty.util.ResourceLeakDetector.Level.DISABLED;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Entry point for styx proxy server.
 */
public final class StyxServer extends AbstractService {
    private static final Logger LOG = getLogger(StyxServer.class);
    private static final String BACKEND_SERVICE_REGISTRY_ID = "backendServiceRegistry";

    static {
        // Disable resource leak detection if no system property supplied
        LOG.debug("Real -Dio.netty.leakDetectionLevel = " + System.getProperty("io.netty.leakDetectionLevel"));

        if (System.getProperty("io.netty.leakDetectionLevel") == null) {
            ResourceLeakDetector.setLevel(DISABLED);
        }

        LOG.debug("Real resource leak detection level = {}", ResourceLeakDetector.getLevel());
    }

    public static void main(String[] args) {
        StyxServer styxServer = createStyxServer(args);
        getRuntime().addShutdownHook(new Thread(() -> styxServer.stopAsync().awaitTerminated()));

        try {
            styxServer.startAsync().awaitRunning();
        } catch (Throwable cause) {
            LOG.error("Error in Styx server startup.", cause);
            System.exit(1);
        }
    }

    private static StyxServer createStyxServer(String[] args) {
        try {
            StartupConfig startupConfig = parseStartupConfig(args);

            LOG.info("Styx home={}", startupConfig.styxHome());
            LOG.info("Styx configFileLocation={}", startupConfig.configFileLocation());
            LOG.info("Styx logConfigLocation={}", startupConfig.logConfigLocation());

            YamlConfig yamlConfig = new YamlConfig(startupConfig.configFileLocation(), System.getProperties());

            StyxConfig styxConfig = new StyxConfig(startupConfig, yamlConfig);

            return new StyxServerBuilder(styxConfig)
                    .logConfigLocationFromEnvironment()
                    .build();

        } catch (Throwable cause) {
            LOG.error("Error in Styx instance creation.", cause);
            throw cause;
        }
    }

    private final HttpServer proxyServer;
    private final HttpServer adminServer;

    private final ServiceManager serviceManager;

    private final MetricRegistry metricRegistry;

    StyxServer(StyxServerBuilder builder) {
        Environment environment = builder.getEnvironment();
        this.metricRegistry = environment.metricRegistry();

        registerVersionMetric(environment);
        registerJvmMetrics(environment.metricRegistry());

        final Map<String, Service> servicesFromConfig = mergeServices(
                loadServices(
                        environment.configuration(),
                        environment,
                        "services",
                        Service.class),
                builder.additionalServices());

        Supplier<Iterable<NamedPlugin>> pluginsSupplier = builder.getPluginsSupplier();

        HttpHandler2 pipeline = styxHttpPipeline(
                environment.styxConfig(),
                userConfiguredHttpPipeline(environment, servicesFromConfig, pluginsSupplier));

        this.proxyServer = new ProxyServerBuilder(environment)
                .httpHandler(pipeline)
                .onStartup(() -> initialisePlugins(pluginsSupplier))
                .build();

        this.proxyServer.addListener(new PluginsNotifierOfProxyState(pluginsSupplier), sameThreadExecutor());

        // TODO: Pass all backend Service Registries to AdminServerBuilder:
        // - only one backendServicesRegistry is passed in to the admin interface. Instead we
        //   should pass all of them:
        this.adminServer = new AdminServerBuilder(environment)
                .backendServicesRegistry((Registry<BackendService>) servicesFromConfig.get(BACKEND_SERVICE_REGISTRY_ID))
                .pluginsSupplier(pluginsSupplier)
                .build();

        this.serviceManager = new ServiceManager(new ArrayList<Service>() {
            {
                add(proxyServer);
                add(adminServer);
                servicesFromConfig.entrySet().stream()
                        .map(Map.Entry::getValue)
                        .forEach(this::add);
            }
        });
    }

    private HttpHandler2 styxHttpPipeline(StyxConfig config, HttpHandler2 interceptorsPipeline) {
        ImmutableList.Builder<HttpInterceptor> builder = ImmutableList.builder();

        boolean loggingEnabled = config.get("request-logging.inbound.enabled", Boolean.class)
                .map(isEnabled -> isEnabled || config.get("request-logging.enabled", Boolean.class).orElse(false))
                .orElse(false);

        boolean longFormatEnabled = config.get("request-logging.inbound.longFormat", Boolean.class)
                .orElse(false);

        if (loggingEnabled) {
            builder.add(new HttpMessageLoggingInterceptor(longFormatEnabled));
        }

        builder.add(new ConfigurationContextResolverInterceptor(EMPTY_CONFIGURATION_CONTEXT_RESOLVER));
        builder.add(new UnexpectedRequestContentLengthRemover());
        builder.add(new ViaHeaderAppendingInterceptor());
        builder.add(new HopByHopHeadersRemovingInterceptor());
        builder.add(new RequestEnrichingInterceptor(config.styxHeaderConfig()));

        return new HttpInterceptorPipeline(builder.build(), interceptorsPipeline);
    }

    private HttpHandler2 userConfiguredHttpPipeline(Environment environment, Map<String, Service> servicesFromConfig, Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
        HttpPipelineFactory pipelineBuilder;
        ConfigVersionResolver configVersionResolver = new ConfigVersionResolver(environment.styxConfig());

        if (configVersionResolver.version() == ROUTING_CONFIG_V1) {
            Registry<BackendService> backendServicesRegistry = (Registry<BackendService>) servicesFromConfig.get(BACKEND_SERVICE_REGISTRY_ID);
            pipelineBuilder = new StaticPipelineFactory(environment, backendServicesRegistry, pluginsSupplier);
        } else {
            pipelineBuilder = new UserConfiguredPipelineFactory(environment, environment.configuration(), pluginsSupplier, servicesFromConfig);
        }

        return pipelineBuilder.build();
    }

    private Map<String, Service> mergeServices(Map<String, Service> configServices, Map<String, Service> additionalServices) {
        ImmutableMap.Builder<String, Service> merged = new ImmutableMap.Builder<>();

        merged.putAll(configServices);
        merged.putAll(additionalServices);

        return merged.build();
    }

    private static void initialisePlugins(Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
        int exceptions = 0;

        for (NamedPlugin plugin : pluginsSupplier.get()) {
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

    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    private static void registerVersionMetric(Environment environment) {
        Optional<Gauge<Integer>> versionGauge = environment.buildInfo().buildNumber()
                .map(buildNumber -> () -> buildNumber);

        if (versionGauge.isPresent()) {
            environment.metricRegistry()
                    .scope("styx")
                    .register("version.buildnumber", versionGauge.get());
        } else {
            LOG.warn("Could not acquire build number from release version: {}", environment.buildInfo());
        }
    }

    private static void registerJvmMetrics(MetricRegistry metricRegistry) {
        RuntimeMXBean runtimeMxBean = getRuntimeMXBean();

        MetricRegistry scoped = metricRegistry.scope("jvm");
        scoped.register("bufferpool", new BufferPoolMetricSet(getPlatformMBeanServer()));
        scoped.register("memory", new MemoryUsageGaugeSet());
        scoped.register("thread", new ThreadStatesGaugeSet());
        scoped.register("gc", new GarbageCollectorMetricSet());
        scoped.register("uptime", (Gauge<Long>) runtimeMxBean::getUptime);
        scoped.register("uptime.formatted", (Gauge<String>) () -> formatTime(runtimeMxBean.getUptime()));
        scoped.register("netty", new NettyAllocatorMetricSet("pooled-allocator", PooledByteBufAllocator.DEFAULT.metric()));
        scoped.register("netty", new NettyAllocatorMetricSet("unpooled-allocator", UnpooledByteBufAllocator.DEFAULT.metric()));
    }

    private static String formatTime(long timeInMilliseconds) {
        Duration duration = Duration.ofMillis(timeInMilliseconds);

        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusHours(duration.toHours()).toMinutes();

        return format("%dd %dh %dm", days, hours, minutes);
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


    private static class PluginsNotifierOfProxyState extends Service.Listener {
        private final Supplier<Iterable<NamedPlugin>> pluginsSupplier;

        PluginsNotifierOfProxyState(Supplier<Iterable<NamedPlugin>> pluginsSupplier) {
            this.pluginsSupplier = pluginsSupplier;
        }

        @Override
        public void stopping(Service.State from) {
            for (NamedPlugin plugin : pluginsSupplier.get()) {
                try {
                    plugin.styxStopping();
                } catch (Exception e) {
                    LOG.error("Error stopping plugin '{}'", plugin.name(), e);
                }
            }
        }
    }

}
