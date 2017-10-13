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
package com.hotels.styx.client;

import com.codahale.metrics.Gauge;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.OriginsInventoryStateChangeListener;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import com.hotels.styx.api.metrics.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.connectionpool.ConnectionPoolFactory;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import org.hamcrest.Matcher;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.support.matchers.ContainsExactlyOneMatcher.containsExactlyOne;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static com.hotels.styx.api.support.HostAndPorts.localHostAndFreePort;
import static com.hotels.styx.client.OriginsInventory.OriginState.ACTIVE;
import static com.hotels.styx.client.OriginsInventory.OriginState.DISABLED;
import static com.hotels.styx.client.connectionpool.ConnectionPoolSettings.defaultSettableConnectionPoolSettings;
import static com.hotels.styx.common.testing.matcher.TransformingMatcher.hasDerivedValue;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OriginsInventoryTest {
    private static final Origin ORIGIN = newOriginBuilder(localHostAndFreePort()).applicationId(GENERIC_APP).id("one").build();

    private final ConnectionPool.Factory connectionFactory = connectionPoolFactory();

    private MetricRegistry metricRegistry;
    private LoggingTestSupport logger;
    private OriginHealthStatusMonitor monitor;
    private InstrumentedEventBus eventBus;
    private OriginsInventory inventory;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
        logger = new LoggingTestSupport(OriginsInventory.class);
        monitor = mock(OriginHealthStatusMonitor.class);
        eventBus = new InstrumentedEventBus();
        inventory = new OriginsInventory(eventBus, GENERIC_APP, monitor, connectionFactory, metricRegistry);
    }

    @AfterMethod
    public void stop() {
        logger.stop();
    }

    @Test
    public void addingANewOriginWillAddToActiveSetAndInitiateTheMonitoring() {
        inventory.addOrigins(ORIGIN);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).monitor(singleton(ORIGIN));

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(1));
    }

    @Test
    public void willNotDisableOriginsNotBelongingToTheApp() {
        inventory.addOrigins(ORIGIN);

        inventory.onCommand(new DisableOrigin(id("some-other-app"), ORIGIN.id()));

        assertThat(inventory, hasActiveOrigins(1));
    }

    @Test
    public void willNotEnableOriginsNotBelongingToTheApp() {
        inventory.addOrigins(ORIGIN);

        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));
        inventory.onCommand(new EnableOrigin(id("some-other-app"), ORIGIN.id()));

        assertThat(inventory, hasNoActiveOrigins());
    }

    @Test
    public void disablingAnOriginRemovesItFromActiveSetAndStopsHealthCheckMonitoring() {
        inventory.addOrigins(ORIGIN);

        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));

        assertThat(inventory, hasNoActiveOrigins());
        assertThat(inventory, hasDisabledOrigins(1));

        verify(monitor).stopMonitoring(singleton(ORIGIN));

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(-1));
    }

    @Test
    public void disablingAnOriginRemovesItFromInactiveSetAndStopsHealthCheckMonitoring() {
        inventory.addOrigins(ORIGIN);

        inventory.originUnhealthy(ORIGIN);
        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));

        assertThat(inventory, hasNoActiveOrigins());
        assertThat(inventory, hasDisabledOrigins(1));

        verify(monitor).stopMonitoring(singleton(ORIGIN));

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(-1));
    }

    @Test
    public void enablingAnOriginWillReInitiateHealthCheckMonitoring() {
        inventory.addOrigins(ORIGIN);

        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));
        inventory.onCommand(new EnableOrigin(ORIGIN.applicationId(), ORIGIN.id()));

        verify(monitor, times(2)).monitor(singleton(ORIGIN));

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(0));
    }

    @Test
    public void removesUnhealthyOriginsFromActiveSet() {
        inventory.addOrigins(ORIGIN);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN);

        assertThat(inventory, hasNoActiveOrigins());

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(0));
    }

    @Test
    public void putsHealthyOriginsBackIntoActiveSet() {
        inventory.addOrigins(ORIGIN);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);

        assertThat(inventory, hasActiveOrigins(1));

        inventory.registerStatusGauges();
        assertThat(gaugeValue("origins.generic-app.one.status"), isValue(1));
    }

    @Test
    public void reportingUpRepeatedlyDoesNotAffectCurrentActiveOrigins() {
        inventory.addOrigins(ORIGIN);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originHealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);

        assertThat(inventory, hasActiveOrigins(1));
    }

    @Test
    public void reportingDownRepeatedlyDoesNotAffectCurrentActiveOrigins() {
        inventory.addOrigins(ORIGIN);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN);
        inventory.originUnhealthy(ORIGIN);
        inventory.originUnhealthy(ORIGIN);

        assertThat(inventory, hasActiveOrigins(0));
    }

    @Test
    public void announcesListenersOnOriginStateChanges() {
        OriginsInventoryStateChangeListener listener = mock(OriginsInventoryStateChangeListener.class);
        inventory.addInventoryStateChangeListener(listener);

        inventory.addOrigins(ORIGIN);
        inventory.originUnhealthy(ORIGIN);

        verify(listener, times(2)).originsInventoryStateChanged(any(OriginsInventorySnapshot.class));
    }

    @Test
    public void logsMessageWhenNewOriginIsAdded() {
        inventory.addOrigins(ORIGIN);

        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "New origin added and activated. Origin=generic-app:one")));
    }

    @Test
    public void logsMessageWhenUnsuccessfulHealthCheckDeactivatesOrigin() {
        inventory.addOrigins(ORIGIN);

        inventory.originUnhealthy(ORIGIN);
        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=one\", change=\"ACTIVE->INACTIVE\"")));
    }

    @Test
    public void logsMessageWhenSuccessfulHealthCheckEventActivatesOrigin() {
        inventory.addOrigins(ORIGIN);
        inventory.originUnhealthy(ORIGIN);

        inventory.originHealthy(ORIGIN);
        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=one\", change=\"INACTIVE->ACTIVE\"")));
    }

    @Test
    public void logsMessageOnlyOnceForOriginHealthyEvent() {
        inventory.addOrigins(ORIGIN);
        inventory.originUnhealthy(ORIGIN);

        inventory.originHealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);
        inventory.originHealthy(ORIGIN);

        assertThat(logger.log(), containsExactlyOne(loggingEvent(INFO, "Origin state change: origin=\"generic-app=one\", change=\"INACTIVE->ACTIVE\"")));
    }

    @Test
    public void logsMessageWhenOriginIsDisabled() {
        inventory.addOrigins(ORIGIN);
        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));

        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=one\", change=\"ACTIVE->DISABLED\"")));
    }

    @Test
    public void logsMessageWhenDisabledOriginWithHealthChecksIsEnabled() {
        inventory.addOrigins(ORIGIN);
        inventory.onCommand(new DisableOrigin(ORIGIN.applicationId(), ORIGIN.id()));
        inventory.onCommand(new EnableOrigin(ORIGIN.applicationId(), ORIGIN.id()));

        assertThat(logger.lastMessage(), is(loggingEvent(INFO,
                "Origin state change: origin=\"generic-app=one\", change=\"DISABLED->INACTIVE\"")));
    }

    @Test
    public void registersToEventBusWhenCreated() {
        assertThat(eventBus.registered, hasItem(inventory));
    }

    @Test
    public void unregistersFromEventBusWhenClosed() {
        inventory.close();

        assertThat(eventBus.registered, not(hasItem(inventory)));
    }

    private static Matcher<OriginsInventory> hasNoActiveOrigins() {
        return hasActiveOrigins(0);
    }

    private static Matcher<OriginsInventory> hasActiveOrigins(int numberOfActiveOrigins) {
        return hasOrigins(numberOfActiveOrigins, ACTIVE);
    }

    private static Matcher<OriginsInventory> hasDisabledOrigins(int numberOfDisabledOrigins) {
        return hasOrigins(numberOfDisabledOrigins, DISABLED);
    }

    private static Matcher<OriginsInventory> hasOrigins(int numberOfOrigins, OriginsInventory.OriginState state) {
        return hasDerivedValue(inventory -> inventory.originCount(state), is(numberOfOrigins));
    }

    private Optional<Integer> gaugeValue(String name) {
        return gauge(name)
                .map(Gauge::getValue)
                .map(value -> (Integer) value);
    }

    private <T> Optional<Gauge<T>> gauge(String name) {
        Gauge<T> gauge = metricRegistry.getGauges().get(name);

        return Optional.ofNullable(gauge);
    }

    private static ConnectionPoolFactory connectionPoolFactory() {
        return new ConnectionPoolFactory.Builder()
                .connectionFactory(new StubConnectionFactory())
                .connectionPoolSettings(defaultSettableConnectionPoolSettings())
                .metricRegistry(new CodaHaleMetricRegistry())
                .build();
    }

    private static class InstrumentedEventBus extends EventBus {
        private final List<Object> registered = new ArrayList<>();

        @Override
        public void register(Object object) {
            super.register(object);
            registered.add(object);
        }

        @Override
        public void unregister(Object object) {
            super.unregister(object);
            registered.remove(object);
        }
    }
}