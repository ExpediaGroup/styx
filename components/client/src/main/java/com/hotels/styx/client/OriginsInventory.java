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
package com.hotels.styx.client;

import com.codahale.metrics.Gauge;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.api.Announcer;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.healthcheck.monitors.NoOriginHealthStatusMonitor;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;
import com.hotels.styx.common.EventProcessor;
import com.hotels.styx.common.QueueDrainingEventProcessor;
import com.hotels.styx.common.StateMachine;
import org.slf4j.Logger;

import javax.annotation.concurrent.ThreadSafe;
import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.client.OriginsInventory.OriginState.ACTIVE;
import static com.hotels.styx.client.OriginsInventory.OriginState.DISABLED;
import static com.hotels.styx.client.OriginsInventory.OriginState.INACTIVE;
import static com.hotels.styx.client.StyxHeaderConfig.ORIGIN_ID_DEFAULT;
import static com.hotels.styx.client.connectionpool.ConnectionPools.simplePoolFactory;
import static com.hotels.styx.common.StyxFutures.await;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * An inventory of the origins configured for a single application.
 */
@ThreadSafe
public final class OriginsInventory
        implements OriginHealthStatusMonitor.Listener,
        OriginsCommandsListener,
        ActiveOrigins,
        OriginsChangeListener.Announcer,
        Closeable,
        EventProcessor {
    private static final Logger LOG = getLogger(OriginsInventory.class);

    private static final HealthyEvent HEALTHY = new HealthyEvent();
    private static final UnhealthyEvent UNHEALTHY = new UnhealthyEvent();

    private final Announcer<OriginsChangeListener> inventoryListeners = Announcer.to(OriginsChangeListener.class);

    private final EventBus eventBus;
    private final Id appId;
    private final OriginHealthStatusMonitor originHealthStatusMonitor;
    private final ConnectionPool.Factory hostConnectionPoolFactory;
    private final StyxHostHttpClient.Factory hostClientFactory;
    private final MetricRegistry metricRegistry;
    private final QueueDrainingEventProcessor eventQueue;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Map<Id, MonitoredOrigin> origins = emptyMap();


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
                            StyxHostHttpClient.Factory hostClientFactory,
                            MetricRegistry metricRegistry) {
        this.eventBus = requireNonNull(eventBus);
        this.appId = requireNonNull(appId);
        this.originHealthStatusMonitor = requireNonNull(originHealthStatusMonitor);
        this.hostConnectionPoolFactory = requireNonNull(hostConnectionPoolFactory);
        this.hostClientFactory = requireNonNull(hostClientFactory);
        this.metricRegistry = requireNonNull(metricRegistry);

        this.eventBus.register(this);
        this.originHealthStatusMonitor.addOriginStatusListener(this);
        eventQueue = new QueueDrainingEventProcessor(this, true);
    }


    @Override
    public void close() {
        eventQueue.submit(new CloseEvent());
    }

    /**
     * Registers origins with this inventory. Connection pools will be created for them and added to the "active" set,
     * they will begin being monitored, and event bus subscribers will be informed that the inventory state has changed.
     *
     * @param newOrigins origins to add
     */
    public void setOrigins(Set<Origin> newOrigins) {
        checkArgument(newOrigins != null && !newOrigins.isEmpty(), "origins list is null or empty");
        eventQueue.submit(new SetOriginsEvent(newOrigins));
    }

    @VisibleForTesting
    public void setOrigins(Origin... origins) {
        setOrigins(ImmutableSet.copyOf(origins));
    }

    @Override
    public void originHealthy(Origin origin) {
        eventQueue.submit(new OriginHealthEvent(origin, HEALTHY));
    }

    @Override
    public void originUnhealthy(Origin origin) {
        eventQueue.submit(new OriginHealthEvent(origin, UNHEALTHY));
    }

    @Subscribe
    @Override
    public void onCommand(EnableOrigin enableOrigin) {
        eventQueue.submit(new EnableOriginCommand(enableOrigin));
    }

    @Subscribe
    @Override
    public void onCommand(DisableOrigin disableOrigin) {
        eventQueue.submit(new DisableOriginCommand(disableOrigin));
    }

    @Subscribe
    @Override
    public void onCommand(GetOriginsInventorySnapshot getOriginsInventorySnapshot) {
        notifyStateChange();
    }

    @Override
    public void addOriginsChangeListener(OriginsChangeListener listener) {
        inventoryListeners.addListener(listener);
    }

    public boolean closed() {
        return closed.get();
    }

    @Override
    public void submit(Object event) {
        if (event instanceof SetOriginsEvent) {
            handleSetOriginsEvent((SetOriginsEvent) event);
        } else if (event instanceof OriginHealthEvent) {
            handleOriginHealthEvent((OriginHealthEvent) event);
        } else if (event instanceof EnableOriginCommand) {
            handleEnableOriginCommand((EnableOriginCommand) event);
        } else if (event instanceof DisableOriginCommand) {
            handleDisableOriginCommand((DisableOriginCommand) event);
        } else if (event instanceof CloseEvent) {
            handleCloseEvent();
        }
    }

    private static class SetOriginsEvent {
        final Set<Origin> newOrigins;

        SetOriginsEvent(Set<Origin> newOrigins) {
            this.newOrigins = newOrigins;
        }
    }

    private static class OriginHealthEvent {
        final Object healthEvent;
        final Origin origin;

        OriginHealthEvent(Origin origin, Object healthy) {
            this.origin = origin;
            this.healthEvent = healthy;
        }
    }

    private static class EnableOriginCommand {
        final EnableOrigin enableOrigin;

        EnableOriginCommand(EnableOrigin enableOrigin) {
            this.enableOrigin = enableOrigin;
        }
    }

    private static class DisableOriginCommand {
        final DisableOrigin disableOrigin;

        DisableOriginCommand(DisableOrigin disableOrigin) {
            this.disableOrigin = disableOrigin;
        }
    }

    private static class CloseEvent {

    }

    private void handleSetOriginsEvent(SetOriginsEvent event) {
        Map<Id, Origin> newOriginsMap = event.newOrigins.stream()
                .collect(toMap(Origin::id, o -> o));

        OriginChanges originChanges = new OriginChanges();

        concat(this.origins.keySet().stream(), newOriginsMap.keySet().stream())
                .collect(toSet())
                .forEach(originId -> {
                    Origin origin = newOriginsMap.get(originId);

                    if (isNewOrigin(originId, origin)) {
                        MonitoredOrigin monitoredOrigin = addMonitoredEndpoint(origin);
                        originChanges.addOrReplaceOrigin(originId, monitoredOrigin);

                    } else if (isUpdatedOrigin(originId, origin)) {
                        MonitoredOrigin monitoredOrigin = changeMonitoredEndpoint(origin);
                        originChanges.addOrReplaceOrigin(originId, monitoredOrigin);

                    } else if (isUnchangedOrigin(originId, origin)) {
                        LOG.info("Existing origin has been left unchanged. Origin={}:{}", appId, origin);
                        originChanges.keepExistingOrigin(originId, this.origins.get(originId));

                    } else if (isRemovedOrigin(originId, origin)) {
                        removeMonitoredEndpoint(originId);
                        originChanges.noteRemovedOrigin();
                    }
                }
        );

        this.origins = originChanges.updatedOrigins();

        if (originChanges.changed()) {
            notifyStateChange();
        }
    }

    private void handleCloseEvent() {
        if (closed.compareAndSet(false, true)) {
            origins.values().forEach(host -> removeMonitoredEndpoint(host.origin.id()));
            this.origins = ImmutableMap.of();
            notifyStateChange();
            eventBus.unregister(this);
        }
    }

    private void handleDisableOriginCommand(DisableOriginCommand event) {
        if (event.disableOrigin.forApp(appId)) {
            onEvent(event.disableOrigin.originId(), event.disableOrigin);
        }
    }

    private void handleEnableOriginCommand(EnableOriginCommand event) {
        if (event.enableOrigin.forApp(appId)) {
            onEvent(event.enableOrigin.originId(), event.enableOrigin);
        }
    }

    private void handleOriginHealthEvent(OriginHealthEvent event) {
        if (event.healthEvent == HEALTHY) {
            if (!(originHealthStatusMonitor instanceof NoOriginHealthStatusMonitor)) {
                onEvent(event.origin, HEALTHY);
            }
        } else if (event.healthEvent == UNHEALTHY) {
            if (!(originHealthStatusMonitor instanceof NoOriginHealthStatusMonitor)) {
                onEvent(event.origin, UNHEALTHY);
            }
        }
    }

    private MonitoredOrigin addMonitoredEndpoint(Origin origin) {
        MonitoredOrigin monitoredOrigin = new MonitoredOrigin(origin);
        metricRegistry.register(monitoredOrigin.gaugeName, (Gauge<Integer>) () -> monitoredOrigin.state().gaugeValue);
        monitoredOrigin.startMonitoring();
        LOG.info("New origin added and activated. Origin={}:{}", appId, monitoredOrigin.origin.id());
        return monitoredOrigin;
    }

    private MonitoredOrigin changeMonitoredEndpoint(Origin origin) {
        MonitoredOrigin oldHost = this.origins.get(origin.id());
        oldHost.close();

        MonitoredOrigin newHost = new MonitoredOrigin(origin);
        newHost.startMonitoring();

        LOG.info("Existing origin has been updated. Origin={}:{}", appId, newHost.origin);
        return newHost;
    }

    private void removeMonitoredEndpoint(Id originId) {
        MonitoredOrigin host = this.origins.get(originId);
        host.close();

        LOG.info("Existing origin has been removed. Origin={}:{}", appId, host.origin.id());
        metricRegistry.deregister(host.gaugeName);
    }

    private boolean isNewOrigin(Id originId, Origin newOrigin) {
        return nonNull(newOrigin) && !this.origins.containsKey(originId);
    }

    private boolean isUnchangedOrigin(Id originId, Origin newOrigin) {
        MonitoredOrigin oldOrigin = this.origins.get(originId);

        return (nonNull(oldOrigin) && nonNull(newOrigin)) && oldOrigin.origin.equals(newOrigin);
    }

    private boolean isUpdatedOrigin(Id originId, Origin newOrigin) {
        MonitoredOrigin oldOrigin = this.origins.get(originId);

        return (nonNull(oldOrigin) && nonNull(newOrigin)) && !oldOrigin.origin.equals(newOrigin);
    }

    private boolean isRemovedOrigin(Id originId, Origin newOrigin) {
        MonitoredOrigin oldOrigin = this.origins.get(originId);

        return nonNull(oldOrigin) && isNull(newOrigin);
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
    public Iterable<RemoteHost> snapshot() {
        return pools(ACTIVE);
    }

    @Override
    public void monitoringEnded(Origin origin) {
        // Do Nothing
    }

    public List<Origin> origins() {
        return origins.values().stream()
                .map(origin -> origin.origin)
                .collect(toList());
    }

    private void notifyStateChange() {
        OriginsSnapshot event = new OriginsSnapshot(appId, pools(ACTIVE), pools(INACTIVE), pools(DISABLED));
        inventoryListeners.announce().originsChanged(event);
        eventBus.post(event);
    }

    private Collection<RemoteHost> pools(OriginState state) {
        return origins.values().stream()
                .filter(origin -> origin.state().equals(state))
                .map(origin -> {
                    HttpHandler hostClient = (request, context) -> fromRxObservable(origin.hostClient.sendRequest(request));
                    return remoteHost(origin.origin, hostClient, origin.hostClient);
                })
                .collect(toList());
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

    private final class MonitoredOrigin {
        private final Origin origin;
        private final ConnectionPool connectionPool;
        private final StateMachine<OriginState> machine;
        private final String gaugeName;
        private final StyxHostHttpClient hostClient;

        private MonitoredOrigin(Origin origin) {
            this.origin = origin;
            this.connectionPool = hostConnectionPoolFactory.create(origin);
            this.hostClient = hostClientFactory.create(connectionPool);

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
            stopMonitoring();
            connectionPool.close();
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

        void startMonitoring() {
            originHealthStatusMonitor.monitor(singleton(origin));
        }

        void stopMonitoring() {
            originHealthStatusMonitor.stopMonitoring(singleton(origin));
        }

        private synchronized void onEvent(Object event) {
            machine.handle(event);
        }

        private OriginState state() {
            return machine.currentState();
        }
    }

    public static Builder newOriginsInventoryBuilder(Id appId) {
        return new Builder(appId);
    }

    public static Builder newOriginsInventoryBuilder(BackendService backendService) {
        return new Builder(backendService.id())
                .connectionPoolFactory(simplePoolFactory(backendService, new CodaHaleMetricRegistry()))
                .initialOrigins(backendService.origins());
    }

    /**
     * A builder for {@link com.hotels.styx.client.OriginsInventory}.
     */
    public static class Builder {
        private final Id appId;
        private OriginHealthStatusMonitor originHealthMonitor = new NoOriginHealthStatusMonitor();
        private MetricRegistry metricsRegistry = new CodaHaleMetricRegistry();
        private EventBus eventBus = new EventBus();
        private ConnectionPool.Factory connectionPoolFactory = simplePoolFactory();
        private StyxHostHttpClient.Factory hostClientFactory;
        private Set<Origin> initialOrigins = emptySet();

        public Builder metricsRegistry(MetricRegistry metricsRegistry) {
            this.metricsRegistry = metricsRegistry;
            return this;
        }

        public Builder connectionPoolFactory(ConnectionPool.Factory connectionPoolFactory) {
            this.connectionPoolFactory = requireNonNull(connectionPoolFactory);
            return this;
        }

        public Builder hostClientFactory(StyxHostHttpClient.Factory hostClientFactory) {
            this.hostClientFactory = requireNonNull(hostClientFactory);
            return this;
        }

        public Builder originHealthMonitor(OriginHealthStatusMonitor originHealthMonitor) {
            this.originHealthMonitor = requireNonNull(originHealthMonitor);
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = requireNonNull(eventBus);
            return this;
        }

        public Builder initialOrigins(Set<Origin> origins) {
            this.initialOrigins = ImmutableSet.copyOf(origins);
            return this;
        }

        public Builder(Id appId) {
            this.appId = requireNonNull(appId);
        }

        public OriginsInventory build() {
            await(originHealthMonitor.start());

            if (hostClientFactory == null) {
                hostClientFactory = (ConnectionPool connectionPool) -> StyxHostHttpClient.create(appId, connectionPool.getOrigin().id(), ORIGIN_ID_DEFAULT, connectionPool);
            }

            OriginsInventory originsInventory = new OriginsInventory(
                    eventBus,
                    appId,
                    originHealthMonitor,
                    connectionPoolFactory,
                    hostClientFactory,
                    metricsRegistry);

            originsInventory.setOrigins(initialOrigins);

            return originsInventory;
        }
    }

    enum OriginState {
        ACTIVE(1), INACTIVE(0), DISABLED(-1);

        private final int gaugeValue;

        OriginState(int gaugeValue) {
            this.gaugeValue = gaugeValue;
        }
    }

    private class OriginChanges {
        ImmutableMap.Builder<Id, MonitoredOrigin> monitoredOrigins = ImmutableMap.builder();
        AtomicBoolean changed = new AtomicBoolean(false);

        void addOrReplaceOrigin(Id originId, MonitoredOrigin origin) {
            monitoredOrigins.put(originId, origin);
            changed.set(true);
        }

        void keepExistingOrigin(Id originId, MonitoredOrigin origin) {
            monitoredOrigins.put(originId, origin);
        }

        void noteRemovedOrigin() {
            changed.set(true);
        }

        boolean changed() {
            return changed.get();
        }

        Map<Id, MonitoredOrigin> updatedOrigins() {
            return monitoredOrigins.build();
        }
    }
}
