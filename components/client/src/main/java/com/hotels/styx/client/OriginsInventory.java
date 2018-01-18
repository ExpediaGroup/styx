/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.client;

import com.codahale.metrics.Gauge;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.api.Announcer;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.OriginsInventoryStateChangeListener;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination;
import com.hotels.styx.client.connectionpool.ConnectionPoolFactory;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitorFactory;
import com.hotels.styx.client.healthcheck.UrlRequestHealthCheck;
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;
import com.hotels.styx.common.StateMachine;
import org.slf4j.Logger;

import javax.annotation.concurrent.ThreadSafe;
import java.io.Closeable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.OriginsInventory.OriginState.ACTIVE;
import static com.hotels.styx.client.OriginsInventory.OriginState.DISABLED;
import static com.hotels.styx.client.OriginsInventory.OriginState.INACTIVE;
import static com.hotels.styx.common.StyxFutures.await;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * An inventory of the origins configured for a single application.
 */
@ThreadSafe
public final class OriginsInventory
        implements OriginHealthStatusMonitor.Listener, OriginsCommandsListener, ActiveOrigins, OriginsInventoryStateChangeListener.Announcer, Closeable {
    private static final Logger LOG = getLogger(OriginsInventory.class);

    private static final HealthyEvent HEALTHY = new HealthyEvent();
    private static final UnhealthyEvent UNHEALTHY = new UnhealthyEvent();

    private final Announcer<OriginsInventoryStateChangeListener> inventoryListeners = Announcer.to(OriginsInventoryStateChangeListener.class);

    private final Map<Id, MonitoredOrigin> origins = new ConcurrentHashMap<>();

    private final EventBus eventBus;
    private final Id appId;
    private final OriginHealthStatusMonitor originHealthStatusMonitor;
    private final ConnectionPool.Factory hostConnectionPoolFactory;
    private final MetricRegistry metricRegistry;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Construct an instance.
     *
     * @param eventBus                  an event bus to subscribe to
     * @param appId                     the application that this inventory's origins are associated with
     * @param originHealthStatusMonitor origin health status monitor
     * @param hostConnectionPoolFactory factory to create connection pools for origins
     * @param metricRegistry            metric registry
     */
    public OriginsInventory(EventBus eventBus,
                            Id appId,
                            OriginHealthStatusMonitor originHealthStatusMonitor,
                            ConnectionPool.Factory hostConnectionPoolFactory,
                            MetricRegistry metricRegistry) {
        this.eventBus = requireNonNull(eventBus);
        this.appId = requireNonNull(appId);
        this.originHealthStatusMonitor = requireNonNull(originHealthStatusMonitor);
        this.hostConnectionPoolFactory = requireNonNull(hostConnectionPoolFactory);
        this.metricRegistry = requireNonNull(metricRegistry);

        this.eventBus.register(this);
        this.originHealthStatusMonitor.addOriginStatusListener(this);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            eventBus.unregister(this);
            origins.values().stream()
                    .peek(MonitoredOrigin::close)
                    .map(o -> o.connectionPool)
                    .forEach(ConnectionPool::close);
            originHealthStatusMonitor.stop();
        }
    }

    public boolean closed() {
        return closed.get();
    }

    @VisibleForTesting
    public void addOrigins(Origin... origins) {
        addOrigins(ImmutableSet.copyOf(origins));
    }

    /**
     * Registers origins with this inventory. Connection pools will be created for them and added to the "active" set,
     * they will begin being monitored, and event bus subscribers will be informed that the inventory state has changed.
     *
     * @param origins origins to add
     */
    public void addOrigins(Set<Origin> origins) {
        checkArgument(origins != null && !origins.isEmpty(), "origins list is null or empty");

        origins.forEach(origin -> {
            MonitoredOrigin monitoredOrigin = new MonitoredOrigin(origin);
            this.origins.put(origin.id(), monitoredOrigin);
            LOG.info("New origin added and activated. Origin={}:{}", appId, origin.id());
        });
    }

    @Override
    public void originHealthy(Origin origin) {
        if (!(originHealthStatusMonitor instanceof NoOriginHealthStatusMonitor)) {
            onEvent(origin, HEALTHY);
        }
    }

    @Override
    public void originUnhealthy(Origin origin) {
        if (!(originHealthStatusMonitor instanceof NoOriginHealthStatusMonitor)) {
            onEvent(origin, UNHEALTHY);
        }
    }

    @Subscribe
    @Override
    public void onCommand(EnableOrigin enableOrigin) {
        if (enableOrigin.forApp(appId)) {
            onEvent(enableOrigin.originId(), enableOrigin);
        }
    }

    @Subscribe
    @Override
    public void onCommand(DisableOrigin disableOrigin) {
        if (disableOrigin.forApp(appId)) {
            onEvent(disableOrigin.originId(), disableOrigin);
        }
    }

    @Subscribe
    @Override
    public void onCommand(GetOriginsInventorySnapshot getOriginsInventorySnapshot) {
        notifyStateChange();
    }

    private void onEvent(Origin origin, Object event) {
        onEvent(origin.id(), event);
    }

    private void onEvent(Id originId, Object event) {
        MonitoredOrigin monitoredOrigin = origins.get(originId);

        if (monitoredOrigin != null) {
            monitoredOrigin.onEvent(event);
        }
    }

    @Override
    public Iterable<ConnectionPool> snapshot() {
        return pools(ACTIVE);
    }

    @Override
    public void monitoringEnded(Origin origin) {
        // Do Nothing
    }

    private void notifyStateChange() {
        OriginsInventorySnapshot event = new OriginsInventorySnapshot(appId, pools(ACTIVE), pools(INACTIVE), pools(DISABLED));
        inventoryListeners.announce().originsInventoryStateChanged(event);
        eventBus.post(event);
    }

    private Collection<ConnectionPool> pools(OriginState state) {
        return origins.values().stream()
                .filter(origin -> origin.state().equals(state))
                .map(origin -> origin.connectionPool)
                .collect(toList());
    }

    @Override
    public void addInventoryStateChangeListener(OriginsInventoryStateChangeListener listener) {
        inventoryListeners.addListener(listener);
    }

    int originCount(OriginState state) {
        return (int) origins.values().stream()
                .map(MonitoredOrigin::state)
                .filter(state::equals)
                .count();
    }

    private static class UnhealthyEvent {
    }

    private static class HealthyEvent {
    }

    public void registerStatusGauges() {
        origins.values().forEach(origin ->
                metricRegistry.register(
                        origin.gaugeName,
                        (Gauge<Integer>) () -> origin.state().gaugeValue));
    }

    private final class MonitoredOrigin {
        private final Origin origin;
        private final ConnectionPool connectionPool;
        private final StateMachine<OriginState> machine;
        private final String gaugeName;

        private MonitoredOrigin(Origin origin) {
            this.origin = origin;
            this.connectionPool = hostConnectionPoolFactory.create(origin);

            startMonitoring();
            notifyStateChange();

            this.machine = new StateMachine.Builder<OriginState>()
                    .initialState(ACTIVE)
                    .onInappropriateEvent((state, event) -> state)
                    .onStateChange(this::onStateChange)

                    .transition(ACTIVE, UnhealthyEvent.class, e -> INACTIVE)
                    .transition(INACTIVE, HealthyEvent.class, e -> ACTIVE)
                    .transition(ACTIVE, DisableOrigin.class, e -> DISABLED)
                    .transition(INACTIVE, DisableOrigin.class, e -> DISABLED)
                    .transition(DISABLED, EnableOrigin.class, e -> INACTIVE)

                    .build();

            this.gaugeName = "origins." + appId + "." + origin.id() + ".status";
        }

        private void close() {
            metricRegistry.deregister(gaugeName);
        }

        private void onStateChange(OriginState oldState, OriginState newState, Object event) {
            if (oldState != newState) {
                LOG.info("Origin state change: origin=\"{}={}\", change=\"{}->{}\"", new Object[]{appId, origin.id(), oldState, newState});

                if (newState == DISABLED) {
                    stopMonitoring();
                } else if (oldState == DISABLED) {
                    startMonitoring();
                }

                notifyStateChange();
            }
        }

        private void startMonitoring() {
            originHealthStatusMonitor.monitor(singleton(origin));
        }

        private void stopMonitoring() {
            originHealthStatusMonitor.stopMonitoring(singleton(origin));
        }

        private synchronized void onEvent(Object event) {
            machine.handle(event);
        }

        private OriginState state() {
            return machine.currentState();
        }
    }

    public static Builder newOriginsInventoryBuilder(BackendService backendService) {
        return new Builder(backendService);
    }

    /**
     * A builder for {@link com.hotels.styx.client.OriginsInventory}.
     */
    public static class Builder {
        private final BackendService backendService;
        private final HttpConfig.Builder httpConfigBuilder = newHttpConfigBuilder();
        private OriginHealthStatusMonitor healthStatusMonitor;
        private OriginHealthStatusMonitor.Factory originHealthStatusMonitorFactory;
        private MetricRegistry metricsRegistry = new CodaHaleMetricRegistry();
        private String version = "";
        private ConnectionPool.Factory connectionPoolFactory;
        private Connection.Factory connectionFactory;
        private EventBus eventBus = new EventBus();
        private int clientWorkerThreadsCount = 1;
        private OriginStatsFactory originStatsFactory;


        public Builder healthStatusMonitor(OriginHealthStatusMonitor healthStatusMonitor) {
            this.healthStatusMonitor = healthStatusMonitor;
            return this;
        }

        public Builder originHealthStatusMonitorFactory(OriginHealthStatusMonitor.Factory originHealthStatusMonitorFactory) {
            this.originHealthStatusMonitorFactory = originHealthStatusMonitorFactory;
            return this;
        }

        public Builder metricsRegistry(MetricRegistry metricsRegistry) {
            this.metricsRegistry = metricsRegistry;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder connectionPoolFactory(ConnectionPool.Factory connectionPoolFactory) {
            this.connectionPoolFactory = connectionPoolFactory;
            return this;
        }

        public Builder connectionFactory(Connection.Factory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder clientWorkerThreadsCount(int clientWorkerThreadsCount) {
            this.clientWorkerThreadsCount = clientWorkerThreadsCount;
            return this;
        }

        public Builder(BackendService backendService) {
            this.backendService = backendService;
        }

        public Builder originStatsFactory(OriginStatsFactory originStatsFactory) {
            this.originStatsFactory = originStatsFactory;
            return this;
        }

        public OriginsInventory build() {
            if (metricsRegistry == null) {
                metricsRegistry = new CodaHaleMetricRegistry();
            }


            healthStatusMonitor = Optional.ofNullable(originHealthStatusMonitorFactory)
                    .orElseGet(OriginHealthStatusMonitorFactory::new)
                    .create(backendService.id(), backendService.healthCheckConfig(), () -> originHealthCheckFunction(metricsRegistry));

            return originsInventory(healthStatusMonitor, httpConfigBuilder.build(), metricsRegistry, originStatsFactory);
        }

        private OriginsInventory originsInventory(OriginHealthStatusMonitor originHealthStatusMonitor, HttpConfig httpConfig,
                                                  MetricRegistry metricsRegistry, OriginStatsFactory originStatsFactory) {
            await(originHealthStatusMonitor.start());

            ConnectionPool.Factory hostConnectionPoolFactory = connectionPoolFactory(backendService.connectionPoolConfig(), httpConfig, metricsRegistry, originStatsFactory);
            OriginsInventory originsInventory = new OriginsInventory(eventBus, backendService.id(), originHealthStatusMonitor, hostConnectionPoolFactory, metricsRegistry);
            originsInventory.addOrigins(backendService.origins());

            return originsInventory;
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

        private ConnectionPool.Factory connectionPoolFactory(ConnectionPool.Settings connectionPoolSettings, HttpConfig httpConfig,
                                                             MetricRegistry metricsRegistry, OriginStatsFactory originStatsFactory) {
            return connectionPoolFactory != null ? connectionPoolFactory : newConnectionPoolFactory(connectionPoolSettings, httpConfig, metricsRegistry, originStatsFactory);
        }

        private ConnectionPoolFactory newConnectionPoolFactory(ConnectionPool.Settings connectionPoolSettings,
                                                               HttpConfig httpConfig, MetricRegistry metricsRegistry, OriginStatsFactory originStatsFactory) {
            Connection.Factory cf = connectionFactory != null
                    ? connectionFactory
                    : new NettyConnectionFactory.Builder()
                    .clientWorkerThreadsCount(clientWorkerThreadsCount)
                    .httpConfig(httpConfig)
                    .tlsSettings(backendService.tlsSettings().orElse(null))
                    .responseTimeoutMillis(backendService.responseTimeoutMillis())
                    .originStatsFactory(originStatsFactory)
                    .build();

            return new ConnectionPoolFactory.Builder()
                    .connectionFactory(cf)
                    .connectionPoolSettings(connectionPoolSettings)
                    .metricRegistry(metricsRegistry)
                    .build();
        }

    }

    enum OriginState {
        ACTIVE(1), INACTIVE(0), DISABLED(-1);

        private final int gaugeValue;

        OriginState(int gaugeValue) {
            this.gaugeValue = gaugeValue;
        }
    }
}
