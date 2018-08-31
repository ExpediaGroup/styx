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
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.connectionpool.ConnectionPoolFactory;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.hamcrest.Matcher;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.common.HostAndPorts.localHostAndFreePort;
import static com.hotels.styx.client.OriginsInventory.OriginState.ACTIVE;
import static com.hotels.styx.client.OriginsInventory.OriginState.DISABLED;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.common.testing.matcher.TransformingMatcher.hasDerivedValue;
import static com.hotels.styx.support.matchers.ContainsExactlyOneMatcher.containsExactlyOne;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OriginsInventoryTest {
    private static final Origin ORIGIN_1 = newOriginBuilder(localHostAndFreePort()).applicationId(GENERIC_APP).id("app-01").build();
    private static final Origin ORIGIN_2 = newOriginBuilder(localHostAndFreePort()).applicationId(GENERIC_APP).id("app-02").build();

    private final ConnectionPool.Factory connectionFactory = connectionPoolFactory();

    private MetricRegistry metricRegistry;
    private LoggingTestSupport logger;
    private OriginHealthStatusMonitor monitor;
    private EventBus eventBus;
    private OriginsInventory inventory;
    private StyxHostHttpClient.Factory hostClientFactory = pool -> mock(StyxHostHttpClient.class);

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
        logger = new LoggingTestSupport(OriginsInventory.class);
        monitor = mock(OriginHealthStatusMonitor.class);
        eventBus = mock(EventBus.class);
        inventory = new OriginsInventory(eventBus, GENERIC_APP, monitor, connectionFactory, hostClientFactory, metricRegistry);
    }

    @AfterMethod
    public void stop() {
        logger.stop();
    }

    /*
     * Setting the origins
     */
    @Test
    public void startsMonitoringNewOrigins() {
        inventory.setOrigins(ORIGIN_1, ORIGIN_2);

        assertThat(inventory, hasActiveOrigins(2));
        verify(monitor).monitor(singleton(ORIGIN_1));
        verify(monitor).monitor(singleton(ORIGIN_2));

        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(1));
        assertThat(gaugeValue("origins.generic-app.app-02.status"), isValue(1));

        verify(eventBus).post(any(OriginsSnapshot.class));
    }

    @Test
    public void updatesOriginPortNumber() throws Exception {
        Origin originV1 = newOriginBuilder("acme.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme.com", 443).applicationId(GENERIC_APP).id("acme-01").build();

        inventory.setOrigins(originV1);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).monitor(singleton(originV1));
        assertThat(gaugeValue("origins.generic-app.acme-01.status"), isValue(1));
        verify(eventBus).post(any(OriginsSnapshot.class));

        inventory.setOrigins(originV2);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).stopMonitoring(singleton(originV1));
        verify(monitor).monitor(singleton(originV2));
        assertThat(gaugeValue("origins.generic-app.acme-01.status"), isValue(1));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void updatesOriginHostName() throws Exception {
        Origin originV1 = newOriginBuilder("acme01.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme02.com", 80).applicationId(GENERIC_APP).id("acme-01").build();

        inventory.setOrigins(originV1);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).monitor(singleton(originV1));
        assertThat(gaugeValue("origins.generic-app.acme-01.status"), isValue(1));
        verify(eventBus).post(any(OriginsSnapshot.class));

        inventory.setOrigins(originV2);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).stopMonitoring(singleton(originV1));
        verify(monitor).monitor(singleton(originV2));
        assertThat(gaugeValue("origins.generic-app.acme-01.status"), isValue(1));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void stopsAndRestartsMonitoringModifiedOrigins() {
        Origin originV1 = newOriginBuilder("acme01.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme02.com", 80).applicationId(GENERIC_APP).id("acme-01").build();

        inventory.setOrigins(originV1);
        verify(monitor).monitor(singleton(originV1));

        inventory.setOrigins(originV2);

        verify(monitor).stopMonitoring(singleton(originV1));
        verify(monitor).monitor(singleton(originV2));
    }

    @Test
    public void shutsConnectionPoolForModifiedOrigin() {
        Origin originV1 = newOriginBuilder("acme01.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme02.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        ConnectionPool.Factory connectionFactory = mock(ConnectionPool.Factory.class);

        ConnectionPool pool1 = mock(ConnectionPool.class);
        ConnectionPool pool2 = mock(ConnectionPool.class);

        when(connectionFactory.create(eq(originV1))).thenReturn(pool1);
        when(connectionFactory.create(eq(originV2))).thenReturn(pool2);

        inventory = new OriginsInventory(eventBus, GENERIC_APP, monitor, connectionFactory, hostClientFactory, metricRegistry);

        inventory.setOrigins(originV1);
        verify(connectionFactory).create(eq(originV1));

        inventory.setOrigins(originV2);
        verify(connectionFactory).create(eq(originV2));

        verify(pool1).close();
    }


    @Test
    public void ignoresUnchangedOrigins() throws Exception {
        inventory.setOrigins(ORIGIN_1, ORIGIN_2);

        assertThat(inventory, hasActiveOrigins(2));
        verify(monitor).monitor(singleton(ORIGIN_1));
        verify(monitor).monitor(singleton(ORIGIN_2));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(1));
        assertThat(gaugeValue("origins.generic-app.app-02.status"), isValue(1));
        verify(eventBus).post(any(OriginsSnapshot.class));

        inventory.setOrigins(ORIGIN_1, ORIGIN_2);

        assertThat(inventory, hasActiveOrigins(2));
        verify(monitor, times(1)).monitor(singleton(ORIGIN_1));
        verify(monitor, times(1)).monitor(singleton(ORIGIN_2));
        verify(eventBus).post(any(OriginsSnapshot.class));
    }

    @Test
    public void removesOrigin() throws Exception {
        inventory.setOrigins(ORIGIN_1, ORIGIN_2);

        assertThat(inventory, hasActiveOrigins(2));
        verify(monitor).monitor(singleton(ORIGIN_1));
        verify(monitor).monitor(singleton(ORIGIN_2));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(1));
        assertThat(gaugeValue("origins.generic-app.app-02.status"), isValue(1));
        verify(eventBus).post(any(OriginsSnapshot.class));

        inventory.setOrigins(ORIGIN_2);

        assertThat(inventory, hasActiveOrigins(1));
        verify(monitor).stopMonitoring(singleton(ORIGIN_1));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isAbsent());
        assertThat(gaugeValue("origins.generic-app.app-02.status"), isValue(1));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }


    @Test
    public void stopsMonitoringRemovedOrigins() {
        Origin originV1 = newOriginBuilder("acme01.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme02.com", 80).applicationId(GENERIC_APP).id("acme-02").build();

        inventory.setOrigins(originV1, originV2);
        verify(monitor).monitor(singleton(originV1));
        verify(monitor).monitor(singleton(originV2));

        inventory.setOrigins(originV1);

        verify(monitor).stopMonitoring(singleton(originV2));
    }

    @Test
    public void shutsConnectionPoolForRemovedOrigin() {
        Origin originV1 = newOriginBuilder("acme01.com", 80).applicationId(GENERIC_APP).id("acme-01").build();
        Origin originV2 = newOriginBuilder("acme02.com", 80).applicationId(GENERIC_APP).id("acme-02").build();
        ConnectionPool.Factory connectionFactory = mock(ConnectionPool.Factory.class);

        ConnectionPool pool1 = mock(ConnectionPool.class);
        when(pool1.getOrigin()).thenReturn(originV1);

        ConnectionPool pool2 = mock(ConnectionPool.class);
        when(pool2.getOrigin()).thenReturn(originV2);

        when(connectionFactory.create(eq(originV1))).thenReturn(pool1);
        when(connectionFactory.create(eq(originV2))).thenReturn(pool2);

        inventory = new OriginsInventory(eventBus, GENERIC_APP, monitor, connectionFactory, hostClientFactory, metricRegistry);

        inventory.setOrigins(originV1, originV2);

        inventory.setOrigins(originV2);

        verify(pool1).close();
    }

    @Test
    public void willNotDisableOriginsNotBelongingToTheApp() {
        inventory.setOrigins(ORIGIN_1);
        verify(eventBus).post(any(OriginsSnapshot.class));

        inventory.onCommand(new DisableOrigin(id("some-other-app"), ORIGIN_1.id()));

        assertThat(inventory, hasActiveOrigins(1));
        verify(eventBus).post(any(OriginsSnapshot.class));
    }

    @Test
    public void willNotEnableOriginsNotBelongingToTheApp() {
        inventory.setOrigins(ORIGIN_1);

        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));
        inventory.onCommand(new EnableOrigin(id("some-other-app"), ORIGIN_1.id()));

        assertThat(inventory, hasNoActiveOrigins());
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void disablingAnOriginRemovesItFromActiveSetAndStopsHealthCheckMonitoring() {
        inventory.setOrigins(ORIGIN_1);

        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));

        assertThat(inventory, hasNoActiveOrigins());
        assertThat(inventory, hasDisabledOrigins(1));

        verify(monitor).stopMonitoring(singleton(ORIGIN_1));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(-1));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void disablingAnOriginRemovesItFromInactiveSetAndStopsHealthCheckMonitoring() {
        inventory.setOrigins(ORIGIN_1);

        inventory.originUnhealthy(ORIGIN_1);
        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));

        assertThat(inventory, hasNoActiveOrigins());
        assertThat(inventory, hasDisabledOrigins(1));

        verify(monitor).stopMonitoring(singleton(ORIGIN_1));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(-1));
        verify(eventBus, times(3)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void enablingAnOriginWillReInitiateHealthCheckMonitoring() {
        inventory.setOrigins(ORIGIN_1);

        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));
        inventory.onCommand(new EnableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));

        verify(monitor, times(2)).monitor(singleton(ORIGIN_1));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(0));
        verify(eventBus, times(3)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void removesUnhealthyOriginsFromActiveSet() {
        inventory.setOrigins(ORIGIN_1);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN_1);

        assertThat(inventory, hasNoActiveOrigins());
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(0));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void putsHealthyOriginsBackIntoActiveSet() {
        inventory.setOrigins(ORIGIN_1);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);

        assertThat(inventory, hasActiveOrigins(1));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isValue(1));
        verify(eventBus, times(3)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void reportingUpRepeatedlyDoesNotAffectCurrentActiveOrigins() {
        inventory.setOrigins(ORIGIN_1);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originHealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);

        assertThat(inventory, hasActiveOrigins(1));
        verify(eventBus, times(1)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void reportingDownRepeatedlyDoesNotAffectCurrentActiveOrigins() {
        inventory.setOrigins(ORIGIN_1);
        assertThat(inventory, hasActiveOrigins(1));

        inventory.originUnhealthy(ORIGIN_1);
        inventory.originUnhealthy(ORIGIN_1);
        inventory.originUnhealthy(ORIGIN_1);

        assertThat(inventory, hasActiveOrigins(0));
        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
    }

    @Test
    public void announcesListenersOnOriginStateChanges() {
        OriginsChangeListener listener = mock(OriginsChangeListener.class);
        inventory.addOriginsChangeListener(listener);

        inventory.setOrigins(ORIGIN_1);
        inventory.originUnhealthy(ORIGIN_1);

        verify(listener, times(2)).originsChanged(any(OriginsSnapshot.class));
    }

    @Test
    public void logsMessageWhenNewOriginIsAdded() {
        inventory.setOrigins(ORIGIN_1);

        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "New origin added and activated. Origin=generic-app:app-01")));
    }

    @Test
    public void logsMessageWhenUnsuccessfulHealthCheckDeactivatesOrigin() {
        inventory.setOrigins(ORIGIN_1);

        inventory.originUnhealthy(ORIGIN_1);
        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=app-01\", change=\"ACTIVE->INACTIVE\"")));
    }

    @Test
    public void logsMessageWhenSuccessfulHealthCheckEventActivatesOrigin() {
        inventory.setOrigins(ORIGIN_1);
        inventory.originUnhealthy(ORIGIN_1);

        inventory.originHealthy(ORIGIN_1);
        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=app-01\", change=\"INACTIVE->ACTIVE\"")));
    }

    @Test
    public void logsMessageOnlyOnceForOriginHealthyEvent() {
        inventory.setOrigins(ORIGIN_1);
        inventory.originUnhealthy(ORIGIN_1);

        inventory.originHealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);
        inventory.originHealthy(ORIGIN_1);

        assertThat(logger.log(), containsExactlyOne(loggingEvent(INFO, "Origin state change: origin=\"generic-app=app-01\", change=\"INACTIVE->ACTIVE\"")));
    }

    @Test
    public void logsMessageWhenOriginIsDisabled() {
        inventory.setOrigins(ORIGIN_1);
        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));

        assertThat(logger.lastMessage(), is(loggingEvent(INFO, "Origin state change: origin=\"generic-app=app-01\", change=\"ACTIVE->DISABLED\"")));
    }

    @Test
    public void logsMessageWhenDisabledOriginWithHealthChecksIsEnabled() {
        inventory.setOrigins(ORIGIN_1);
        inventory.onCommand(new DisableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));
        inventory.onCommand(new EnableOrigin(ORIGIN_1.applicationId(), ORIGIN_1.id()));

        assertThat(logger.lastMessage(), is(loggingEvent(INFO,
                "Origin state change: origin=\"generic-app=app-01\", change=\"DISABLED->INACTIVE\"")));
    }

    @Test
    public void registersToEventBusWhenCreated() {
        verify(eventBus).register(eq(inventory));
    }

    @Test
    public void stopsMonitoringAndUnregistersWhenClosed() {
        ConnectionPool.Factory connectionFactory = mock(ConnectionPool.Factory.class);

        ConnectionPool pool1 = mock(ConnectionPool.class);
        when(pool1.getOrigin()).thenReturn(ORIGIN_1);

        ConnectionPool pool2 = mock(ConnectionPool.class);
        when(pool2.getOrigin()).thenReturn(ORIGIN_2);

        when(connectionFactory.create(eq(ORIGIN_1))).thenReturn(pool1);
        when(connectionFactory.create(eq(ORIGIN_2))).thenReturn(pool2);

        inventory = new OriginsInventory(eventBus, GENERIC_APP, monitor, connectionFactory, hostClientFactory, metricRegistry);
        inventory.setOrigins(ORIGIN_1, ORIGIN_2);
        inventory.close();

        verify(monitor).stopMonitoring(eq(ImmutableSet.of(ORIGIN_1)));
        verify(monitor).stopMonitoring(eq(ImmutableSet.of(ORIGIN_2)));
        assertThat(gaugeValue("origins.generic-app.app-01.status"), isAbsent());
        assertThat(gaugeValue("origins.generic-app.app-02.status"), isAbsent());

        verify(pool1).close();
        verify(pool2).close();

        verify(eventBus, times(2)).post(any(OriginsSnapshot.class));
        verify(eventBus).unregister(eq(inventory));
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
                .connectionPoolSettings(defaultConnectionPoolSettings())
                .metricRegistry(new CodaHaleMetricRegistry())
                .build();
    }

}