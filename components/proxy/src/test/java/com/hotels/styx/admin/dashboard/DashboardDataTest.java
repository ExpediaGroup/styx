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
package com.hotels.styx.admin.dashboard;

import com.codahale.metrics.Gauge;
import com.google.common.eventbus.EventBus;
import com.hotels.styx.Version;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.applications.BackendServices;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static com.google.common.collect.Sets.newHashSet;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.applications.BackendServices.newBackendServices;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DashboardDataTest {
    static final BackendServices DEFAULT_APPLICATIONS = newBackendServices(application("app", origin("app-01", "localhost", 9090)));

    MetricRegistry metricRegistry;
    EventBus eventBus;
    MemoryBackedRegistry<BackendService> backendServicesRegistry;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
        eventBus = new EventBus();
        backendServicesRegistry = new MemoryBackedRegistry<>();

        DEFAULT_APPLICATIONS.forEach(backendServicesRegistry::add);
    }

    @Test
    public void providesServerId() {
        DashboardData dashboardData = newDashboardData("styx-prod1-presentation-01", "releaseTag", backendServicesRegistry);

        assertThat(dashboardData.server().id(), is("styx-prod1-presentation-01"));
    }

    @Test
    public void providesVersion() {
        DashboardData dashboardData = newDashboardData("serverId", "STYX.0.4.283", backendServicesRegistry);

        assertThat(dashboardData.server().version(), is("0.4.283"));
    }

    @Test
    public void providesUptime() {
        metricRegistry.register("jvm.uptime.formatted", gauge("1d 3h 2m"));

        assertThat(newDashboardData().server().uptime(), is("1d 3h 2m"));
    }

    @Test
    public void providesStyxErrorResponseCodes() {
        metricRegistry.counter("styx.response.status.500").inc(111);
        metricRegistry.counter("styx.response.status.502").inc(222);

        Map<String, Integer> responses = newDashboardData().server().responses();

        assertThat(responses.get("500"), is(111));
        assertThat(responses.get("502"), is(222));
        assertThat(responses.get("5xx"), is(333));
    }

    @Test
    public void providesOriginsErrorResponseCodes() {
        metricRegistry.counter("origins.response.status.500").inc(111);
        metricRegistry.counter("origins.response.status.502").inc(222);

        Map<String, Integer> responses = newDashboardData().downstream().responses();

        assertThat(responses.get("500"), is(111));
        assertThat(responses.get("502"), is(222));
        assertThat(responses.get("5xx"), is(333));
    }

    @Test
    public void providesBackendInformation() {
        DashboardData.Backend backend = newDashboardData("styx-prod1-presentation-01", "releaseTag", backendServicesRegistry).downstream().firstBackend();

        assertThat(backend.id(), is("styx-prod1-presentation-01-app"));
        assertThat(backend.name(), is("app"));
    }

    @Test
    public void addsBackendDataWhenBackendsAreAdded() {
        DashboardData.Downstream downstream = newDashboardData("styx-prod1-presentation-01", "releaseTag", backendServicesRegistry).downstream();

        backendServicesRegistry.add(application("landing", origin("landing-01", "localhost", 9090)));

        assertThat(downstream.backendIds(), containsInAnyOrder("styx-prod1-presentation-01-app", "styx-prod1-presentation-01-landing"));

        assertThat(downstream.backend("styx-prod1-presentation-01-app").name(), is("app"));
        assertThat(downstream.backend("styx-prod1-presentation-01-landing").name(), is("landing"));
    }

    @Test
    public void removesBackendDataWhenBackendsAreRemoved() {
        backendServicesRegistry.add(application("lapp", origin("landing-01", "localhost", 9090)));
        backendServicesRegistry.add(application("app", origin("app-01", "localhost", 9090)));

        DashboardData.Downstream downstream = newDashboardData("styx-prod1-presentation-01", "releaseTag", backendServicesRegistry).downstream();

        backendServicesRegistry.removeById(id("app"));

        assertThat(downstream.backendIds(), containsInAnyOrder("styx-prod1-presentation-01-lapp"));
        assertThat(downstream.backend("styx-prod1-presentation-01-lapp").name(), is("lapp"));
    }

    @Test
    public void providesBackendResponseCodes() {
        metricRegistry.meter("origins.app.requests.response.status.200").mark(123);
        metricRegistry.meter("origins.app.requests.response.status.500").mark(111);
        metricRegistry.meter("origins.app.requests.response.status.502").mark(222);

        Map<String, Integer> responses = newDashboardData().downstream().firstBackend().responses();

        assertThat(responses.get("200"), is(123));
        assertThat(responses.get("2xx"), is(123));

        assertThat(responses.get("500"), is(111));
        assertThat(responses.get("502"), is(222));
        assertThat(responses.get("5xx"), is(333));
    }

    @Test
    public void providesBackendRequestData() {
        metricRegistry.meter("origins.app.requests.success-rate").mark(123);
        metricRegistry.meter("origins.app.requests.error-rate").mark(111);
        metricRegistry.timer("origins.app.requests.latency").update(1000, MILLISECONDS);

        DashboardData.Requests requests = newDashboardData().downstream().firstBackend().requests();

        assertThat(requests.successRate().count(), is(123L));
        assertThat(requests.errorRate().count(), is(111L));
        assertThat(requests.latency().p50(), is(closeTo(1000.0, 5)));
    }

    @Test
    public void providesBackendStatuses() {
        BackendServices backendServices = newBackendServices(
                application("app",
                        origin("app-01", "localhost", 9090),
                        origin("app-02", "localhost", 9091)));

        MemoryBackedRegistry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        backendServices.forEach(backendServicesRegistry::add);

        DashboardData.Backend backend = newDashboardData(backendServicesRegistry).downstream().firstBackend();

        eventBus.post(new OriginsSnapshot(id("app"),
                singleton(pool(origin("app", "app-01", "localhost", 9090))),
                emptyList(),
                singleton(pool(origin("app", "app-02", "localhost", 9091)))));

        assertThat(backend.statuses(), containsInAnyOrder("active", "disabled"));
    }

    @Test
    public void providesBackendTotalConnections() {
        BackendServices backendServices = newBackendServices(
                application("app",
                        origin("app-01", "localhost", 9090),
                        origin("app-02", "localhost", 9091)));

        MemoryBackedRegistry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        backendServices.forEach(backendServicesRegistry::add);

        metricRegistry.register("origins.app.app-01.connectionspool.available-connections", gauge(100));
        metricRegistry.register("origins.app.app-01.connectionspool.busy-connections", gauge(300));
        metricRegistry.register("origins.app.app-01.connectionspool.pending-connections", gauge(500));

        metricRegistry.register("origins.app.app-02.connectionspool.available-connections", gauge(200));
        metricRegistry.register("origins.app.app-02.connectionspool.busy-connections", gauge(400));
        metricRegistry.register("origins.app.app-02.connectionspool.pending-connections", gauge(600));

        DashboardData.Backend backend = newDashboardData(backendServicesRegistry).downstream().firstBackend();

        DashboardData.ConnectionsPoolsAggregate connectionsPool = backend.totalConnections();

        assertThat(connectionsPool.available(), is(300));
        assertThat(connectionsPool.busy(), is(700));
        assertThat(connectionsPool.pending(), is(1100));
    }

    private RemoteHost pool(Origin origin) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.getOrigin()).thenReturn(origin);
        return remoteHost(origin, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class));
    }

    @Test
    public void providesOriginInformation() {
        DashboardData.Origin origin = newDashboardData().downstream().firstBackend().firstOrigin();

        assertThat(origin.id(), is("app-01"));
        assertThat(origin.name(), is("app-01"));
    }

    @Test
    public void providesOriginResponseCodes() {
        metricRegistry.meter("origins.app.app-01.requests.response.status.200").mark(123);
        metricRegistry.meter("origins.app.app-01.requests.response.status.500").mark(111);
        metricRegistry.meter("origins.app.app-01.requests.response.status.502").mark(222);

        Map<String, Integer> responses = newDashboardData().downstream().firstBackend().firstOrigin().responses();

        assertThat(responses.get("200"), is(123));
        assertThat(responses.get("2xx"), is(123));

        assertThat(responses.get("500"), is(111));
        assertThat(responses.get("502"), is(222));
        assertThat(responses.get("5xx"), is(333));
    }

    @Test
    public void providesOriginRequestData() {
        metricRegistry.meter("origins.app.app-01.requests.success-rate").mark(123);
        metricRegistry.meter("origins.app.app-01.requests.error-rate").mark(111);
        metricRegistry.timer("origins.app.app-01.requests.latency").update(1000, MILLISECONDS);

        DashboardData.Requests requests = newDashboardData().downstream().firstBackend().firstOrigin().requests();

        assertThat(requests.successRate().count(), is(123L));
        assertThat(requests.errorRate().count(), is(111L));
        assertThat(requests.latency().p50(), is(closeTo(1000.0, 5)));
        assertThat(requests.errorPercentage(), is(closeTo(47.0, 0.5)));
    }

    @Test
    public void providesOriginStatus() {
        metricRegistry.register("origins.app.app-01.status", gauge("active"));

        DashboardData.Origin origin = newDashboardData().downstream().firstBackend().firstOrigin();

        eventBus.post(new OriginsSnapshot(id("app"),
                singleton(pool(origin("app", "app-01", "localhost", 9090))),
                emptyList(),
                emptyList()));

        assertThat(origin.status(), is("active"));
    }

    @Test
    public void providesOriginConnectionPoolData() {
        metricRegistry.register("origins.app.app-01.connectionspool.available-connections", gauge(123));
        metricRegistry.register("origins.app.app-01.connectionspool.busy-connections", gauge(234));
        metricRegistry.register("origins.app.app-01.connectionspool.pending-connections", gauge(345));

        DashboardData.Origin.ConnectionsPool connectionsPool = newDashboardData().downstream().firstBackend().firstOrigin().connectionsPool();

        assertThat(connectionsPool.available(), is(123));
        assertThat(connectionsPool.busy(), is(234));
        assertThat(connectionsPool.pending(), is(345));
    }

    @Test
    public void unsubscribesFromEventBus() {
        EventBus eventBus = mock(EventBus.class);
        MemoryBackedRegistry<BackendService> backendServicesRegistry = new MemoryBackedRegistry<>();
        backendServicesRegistry.add(application("app", origin("app-01", "localhost", 9090)));
        backendServicesRegistry.add(application("test", origin("test-01", "localhost", 9090)));

        DashboardData dashbaord = new DashboardData(metricRegistry, backendServicesRegistry, "styx-prod1-presentation-01", new Version("releaseTag"), eventBus);

        // Twice for each backend. One during backend construction, another from BackendServicesRegistry listener callback.
        verify(eventBus, times(4)).register(any(DashboardData.Origin.class));

        dashbaord.unregister();

        verify(eventBus, times(4)).unregister(any(DashboardData.Origin.class));
    }


    // makes the generics explicit for the compiler (to avoid having a cast every time () -> value is used).
    private <T> Gauge<T> gauge(T value) {
        return () -> value;
    }

    private DashboardData newDashboardData() {
        return newDashboardData(backendServicesRegistry);
    }

    private DashboardData newDashboardData(Registry<BackendService> backendServiceRegistry) {
        return newDashboardData("serverId", "releaseTag", backendServiceRegistry);
    }

    private DashboardData newDashboardData(String serverId, String releaseTag, Registry<BackendService> backendServiceRegistry) {
        return new DashboardData(metricRegistry, backendServiceRegistry, serverId, new Version(releaseTag), eventBus);
    }

    private static BackendService application(String id, Origin... origins) {
        return newBackendServiceBuilder()
                .id(id)
                .origins(newHashSet(origins))
                .build();
    }

    private static Origin origin(String id, String host, int port) {
        return newOriginBuilder(host, port)
                .id(id)
                .build();
    }

    private static Origin origin(String appId, String id, String host, int port) {
        return newOriginBuilder(host, port)
                .applicationId(appId)
                .id(id)
                .build();
    }
}