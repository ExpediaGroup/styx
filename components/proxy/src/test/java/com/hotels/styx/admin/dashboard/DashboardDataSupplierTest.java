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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.client.connectionpool.ConnectionPool;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardDataSupplierTest {
    Environment environment = new Environment.Builder().build();
    StyxConfig styxConfig = StyxConfig.fromYaml("jvmRouteName: STYXPRES");
    AtomicInteger nextPort = new AtomicInteger(9090);

    @Test
    public void receivesBackendUpdates() {
        MemoryBackedRegistry<BackendService> registry = new MemoryBackedRegistry<>();
        DashboardDataSupplier supplier = new DashboardDataSupplier(registry, environment, styxConfig);

        registry.add(backend("foo", origin("foo1")));
        assertThat(supplier.get().downstream().firstBackend().id(), is("STYXPRES-foo"));

        registry.add(backend("bar", origin("bar1")));
        assertThat(supplier.get().downstream().backendIds(), containsInAnyOrder("STYXPRES-foo", "STYXPRES-bar"));
    }

    @Test
    public void originsHaveStatuses() throws JsonProcessingException {
        MemoryBackedRegistry<BackendService> registry = new MemoryBackedRegistry<>();
        DashboardDataSupplier supplier = new DashboardDataSupplier(registry, environment, styxConfig);

        Origin foo1 = origin("foo1");
        Origin foo2 = origin("foo2");

        registry.add(backend("foo", foo1, foo2));
        registry.add(backend("bar", origin("bar1")));

        // Set statuses
        environment.eventBus().post(new OriginsSnapshot(id("foo"), pools(foo1), pools(foo2), pools()));

        DashboardData.Downstream downstream = supplier.get().downstream();
        DashboardData.Backend fooBackend = downstream.backend("STYXPRES-foo");

        assertThat(downstream.backendIds(), containsInAnyOrder("STYXPRES-foo", "STYXPRES-bar"));
        assertThat(fooBackend.statusesByOriginId(), is(equalTo(ImmutableMap.of("foo1", "active", "foo2", "inactive"))));
        assertThat(fooBackend.origin("foo1").status(), is("active"));


        // Set statuses again
        environment.eventBus().post(new OriginsSnapshot(id("foo"), pools(), pools(foo1), pools(foo2)));

        fooBackend = supplier.get().downstream().backend("STYXPRES-foo");

        assertThat(fooBackend.statusesByOriginId(), is(equalTo(ImmutableMap.of("foo1", "inactive", "foo2", "disabled"))));
        assertThat(fooBackend.origin("foo1").status(), is("inactive"));
    }

    private Collection<RemoteHost> pools(Origin... origins) {
        return asList(origins).stream()
                .map(this::pool)
                .map(pool -> remoteHost(pool.getOrigin(), mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)))
                .collect(toList());
    }

    private Origin origin(String id) {
        return newOriginBuilder("localhost", nextPort.getAndIncrement())
                .id(id)
                .build();
    }

    private ConnectionPool pool(Origin origin) {
        ConnectionPool pool = mock(ConnectionPool.class);
        when(pool.getOrigin()).thenReturn(origin);
        return pool;
    }

    private BackendService backend(String id, Origin... origins) {
        return newBackendServiceBuilder()
                .id(id)
                .origins(origins)
                .build();
    }
}
