package com.hotels.styx.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.ConnectionPoolProvider;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.applications.OriginStats;
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination;
import com.hotels.styx.client.connectionpool.ConnectionPoolFactory;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitorFactory;
import com.hotels.styx.client.healthcheck.UrlRequestHealthCheck;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import org.slf4j.Logger;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static org.slf4j.LoggerFactory.getLogger;

public class BackendServiceConnectionPoolProvider implements ConnectionPoolProvider {
    private static final Logger LOGGER = getLogger(StyxHttpClient.class);

    private final BackendService backendService;
    private final Id id;
    private final LoadBalancingStrategy loadBalancingStrategy;
    private final OriginHealthStatusMonitor originHealthStatusMonitor;
    private final OriginsInventory originsInventory;
    private final OriginStatsFactory originStatsFactory;
    private BackendServiceConnectionPoolProvider(Builder builder) {

        this.backendService = builder.backendService;
        this.id = backendService.id();
        this.loadBalancingStrategy = backendService.stickySessionConfig().stickySessionEnabled()
                ? new StickySessionLoadBalancingStrategy(builder.loadBalancingStrategy)
                : nonStickyLoadBalancingStrategy(builder);
        this.originsInventory = builder.inventory;
        this.originsInventory.addInventoryStateChangeListener(loadBalancingStrategy);
        this.originHealthStatusMonitor = builder.healthStatusMonitor;

        this.originStatsFactory = builder.originStatsFactory;

    }

    @Override
    public void close() {
        this.originHealthStatusMonitor.stopAsync();
        this.originsInventory.close();
    }

    @Override
    public Optional<ConnectionPool> connectionPool(HttpRequest httpRequest) {
        LoadBalancingStrategy.Context lbContext = new LBContext(httpRequest, id, originStatsFactory);
        Iterable<ConnectionPool> votedOrigins = loadBalancingStrategy.vote(originsInventory.snapshot(), lbContext);
        return Optional.ofNullable(getFirst(votedOrigins, null));
    }

    @Override
    public void registerStatusGauges() {
        originsInventory.registerStatusGauges();
    }

    public boolean isHttps() {
        return backendService.tlsSettings().isPresent();
    }

    Id id() {
        return id;
    }

    OriginsInventory originsInventory() {
        return originsInventory;
    }

    LoadBalancingStrategy loadBalancingStrategy() {
        return loadBalancingStrategy;
    }

    private static LoadBalancingStrategy nonStickyLoadBalancingStrategy(Builder builder) {
        if (builder.originRestrictionCookie == null) {
            LOGGER.info("originRestrictionCookie not specified - origin restriction disabled");

            return builder.loadBalancingStrategy;
        }

        LOGGER.info("originRestrictionCookie specified as {} - origin restriction will apply when this cookie is sent", builder.originRestrictionCookie);

        return new OriginRestrictionLoadBalancingStrategy(builder.loadBalancingStrategy, builder.originRestrictionCookie);
    }

    private static class LBContext implements LoadBalancingStrategy.Context {
        private final HttpRequest request;
        private final Id id;
        private final OriginStatsFactory originStatsFactory;

        LBContext(HttpRequest request, Id id, OriginStatsFactory originStatsFactory) {
            this.request = request;
            this.id = id;
            this.originStatsFactory = originStatsFactory;
        }

        @Override
        public Id appId() {
            return id;
        }

        @Override
        public HttpRequest currentRequest() {
            return request;
        }

        @Override
        public double oneMinuteRateForStatusCode5xx(Origin origin) {
            OriginStats originStats = originStatsFactory.originStats(origin);
            return originStats.oneMinuteErrorRate();
        }
    }

    /**
     * A builder for {@link com.hotels.styx.client.StyxHttpClient}.
     */
    public static class Builder {
        private final BackendService backendService;
        private EventBus eventBus = new EventBus();
        private MetricRegistry metricsRegistry = new CodaHaleMetricRegistry();
        private final HttpConfig.Builder httpConfigBuilder = newHttpConfigBuilder();
        private int clientWorkerThreadsCount = 1;
        private Connection.Factory connectionFactory;
        private LoadBalancingStrategy loadBalancingStrategy = new RoundRobinStrategy();
        private ConnectionPool.Factory connectionPoolFactory;
        private String version = "";
        private String originRestrictionCookie;
        private OriginHealthStatusMonitor.Factory originHealthStatusMonitorFactory;
        private OriginHealthStatusMonitor healthStatusMonitor;
        private OriginsInventory inventory;
        private OriginStatsFactory originStatsFactory;

        public Builder(BackendService backendService) {
            this.backendService = checkNotNull(backendService);
        }

        public Builder originRestrictionCookie(String originRestrictionCookie) {
            this.originRestrictionCookie = originRestrictionCookie;
            return this;
        }

        public Builder metricsRegistry(MetricRegistry metricsRegistry) {
            this.metricsRegistry = checkNotNull(metricsRegistry);
            return this;
        }

        public Builder clientWorkerThreadsCount(int clientWorkerThreadsCount) {
            this.clientWorkerThreadsCount = clientWorkerThreadsCount;
            return this;
        }

        public Builder connectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = checkNotNull(connectionFactory);
            return this;
        }

        @VisibleForTesting
        Builder connectionPoolFactory(ConnectionPool.Factory connectionPoolFactory) {
            this.connectionPoolFactory = checkNotNull(connectionPoolFactory);
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = checkNotNull(eventBus);
            return this;
        }
        public Builder loadBalancingStrategy(LoadBalancingStrategy loadBalancingStrategy) {
            this.loadBalancingStrategy = loadBalancingStrategy;
            return this;
        }

        public Builder originHealthStatusMonitorFactory(OriginHealthStatusMonitor.Factory originHealthStatusMonitorFactory) {
            this.originHealthStatusMonitorFactory = checkNotNull(originHealthStatusMonitorFactory);
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder originStatsFactory(OriginStatsFactory originStatsFactory) {
            this.originStatsFactory = originStatsFactory;
            return this;
        }

        private OriginsInventory originsInventory(OriginHealthStatusMonitor originHealthStatusMonitor, HttpConfig httpConfig, MetricRegistry metricsRegistry) {
            originHealthStatusMonitor.startAsync().awaitRunning();

            ConnectionPool.Factory hostConnectionPoolFactory = connectionPoolFactory(backendService.connectionPoolConfig(), httpConfig, metricsRegistry);
            OriginsInventory originsInventory = new OriginsInventory(eventBus, backendService.id(), originHealthStatusMonitor, hostConnectionPoolFactory, metricsRegistry);
            originsInventory.addOrigins(backendService.origins());

            return originsInventory;
        }

        private ConnectionPool.Factory connectionPoolFactory(ConnectionPool.Settings connectionPoolSettings, HttpConfig httpConfig, MetricRegistry metricsRegistry) {
            return connectionPoolFactory != null ? connectionPoolFactory : newConnectionPoolFactory(connectionPoolSettings, httpConfig, metricsRegistry);
        }

        private ConnectionPoolFactory newConnectionPoolFactory(ConnectionPool.Settings connectionPoolSettings, HttpConfig httpConfig, MetricRegistry metricsRegistry) {
            Connection.Factory cf = connectionFactory != null
                    ? connectionFactory
                    : new NettyConnectionFactory.Builder()
                    .clientWorkerThreadsCount(clientWorkerThreadsCount)
                    .httpConfig(httpConfig)
                    .tlsSettings(backendService.tlsSettings().orElse(null))
                    .build();

            return new ConnectionPoolFactory.Builder()
                    .connectionFactory(cf)
                    .connectionPoolSettings(connectionPoolSettings)
                    .metricRegistry(metricsRegistry)
                    .build();
        }

        private OriginHealthCheckFunction originHealthCheckFunction(MetricRegistry metricRegistry) {
            NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                    .name("Health-Check-Monitor-" + backendService.id())
                    .tlsSettings(backendService.tlsSettings().orElse(null))
                    .build();

            ConnectionSettings connectionSettings = new ConnectionSettings(
                    backendService.connectionPoolConfig().connectTimeoutMillis(),
                    backendService.healthCheckConfig().timeoutMillis());

            HttpClient client = new SimpleNettyHttpClient.Builder()
                    .userAgent("Styx/" + version)
                    .connectionDestinationFactory(
                            new CloseAfterUseConnectionDestination.Factory()
                                    .connectionSettings(connectionSettings)
                                    .connectionFactory(connectionFactory))
                    .build();

            String healthCheckUri = backendService.healthCheckConfig()
                    .uri()
                    .orElseThrow(() -> new IllegalArgumentException("Health check URI missing for " + backendService.id()));

            return new UrlRequestHealthCheck(healthCheckUri, client, metricRegistry);
        }

        public BackendServiceConnectionPoolProvider build() {
            if (metricsRegistry == null) {
                metricsRegistry = new CodaHaleMetricRegistry();
            }

            healthStatusMonitor = Optional.ofNullable(originHealthStatusMonitorFactory)
                    .orElseGet(OriginHealthStatusMonitorFactory::new)
                    .create(backendService.id(), backendService.healthCheckConfig(), () -> originHealthCheckFunction(metricsRegistry));

            inventory = originsInventory(healthStatusMonitor, httpConfigBuilder.build(), metricsRegistry);

            return new BackendServiceConnectionPoolProvider(this);
        }
    }
}
