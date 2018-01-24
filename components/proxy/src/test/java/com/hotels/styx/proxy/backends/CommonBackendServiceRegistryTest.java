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
package com.hotels.styx.proxy.backends;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.RegistryServiceAdapter;
import com.hotels.styx.proxy.BackendServiceClientFactory;
import com.hotels.styx.proxy.backends.CommonBackendServiceRegistry.StyxBackendService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.filterKeys;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.client.connectionpool.ConnectionPoolSettings.defaultSettableConnectionPoolSettings;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommonBackendServiceRegistryTest {

    private Environment environment = new Environment.Builder().build();
    private BackendService backendA;
    private BackendService backendB;
    private BackendService backendC;

    private HttpClient clientA;
    private HttpClient clientB;
    private HttpClient clientC;

    private BackendServiceClientFactory clientFactory;
    private OriginInventoryFactory inventoryFactory;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        backendA = newBackendServiceBuilder().id("app-a").origins(newOriginBuilder("localhost", 8080).build()).build();
        backendB = newBackendServiceBuilder().id("app-b").origins(newOriginBuilder("localhost", 8080).build()).build();
        backendC = newBackendServiceBuilder().id("app-c").origins(newOriginBuilder("localhost", 8080).build()).build();

        inventoryFactory = new OriginInventoryFactory(environment, 10);
        clientFactory = mock(BackendServiceClientFactory.class);

        clientA = mock(HttpClient.class);
        clientB = mock(HttpClient.class);
        clientC = mock(HttpClient.class);

        when(clientFactory.createClient(eq(backendA), any(OriginsInventory.class))).thenReturn(clientA);
        when(clientFactory.createClient(eq(backendB), any(OriginsInventory.class))).thenReturn(clientB);
        when(clientFactory.createClient(eq(backendC), any(OriginsInventory.class))).thenReturn(clientC);
    }

    @Captor
    private ArgumentCaptor<Registry.Changes<StyxBackendService>> captor;

    @Test
    public void listensToProviderOnlyAfterInitialSetup() {
        /*
         * To avoid concurrency issues, BackendServiceRegistry must first establish its initial state before
         * registering for change notifications.
         */
        Registry<BackendService> registry = mock(Registry.class);
        when(registry.get()).thenReturn(ImmutableList.of());

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);

        InOrder inOrder = inOrder(registry);
        inOrder.verify(registry).get();
        inOrder.verify(registry).addListener(eq(provider));
    }

    @Test
    public void createsInternalStyxBackendServiceObjects() {
        MockProvider registry = new MockProvider();
        registry.setBackends(ImmutableList.of(backendA, backendB, backendC));

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);

        assertThat(provider.backendService(id("app-a")), instanceOf(StyxBackendService.class));
        assertThat(provider.backendService(id("app-a")).id(), is(id("app-a")));
        assertThat(provider.backendService(id("app-a")).httpClient(), is(clientA));
        assertThat(provider.backendService(id("app-a")).originsInventory(), instanceOf(OriginsInventory.class));

        assertThat(provider.backendService(id("app-b")), instanceOf(StyxBackendService.class));
        assertThat(provider.backendService(id("app-b")).id(), is(id("app-b")));
        assertThat(provider.backendService(id("app-b")).httpClient(), is(clientB));
        assertThat(provider.backendService(id("app-b")).originsInventory(), instanceOf(OriginsInventory.class));

        assertThat(provider.backendService(id("app-c")), instanceOf(StyxBackendService.class));
        assertThat(provider.backendService(id("app-c")).id(), is(id("app-c")));
        assertThat(provider.backendService(id("app-c")).httpClient(), is(clientC));
        assertThat(provider.backendService(id("app-c")).originsInventory(), instanceOf(OriginsInventory.class));
    }

    @Test
    public void addsNewOriginsFromOnChangeNotification() {
        MockProvider registry = new MockProvider();

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);

        provider.onChange(
                changeSetBuilder()
                        .added(backendA)
                        .build());

        assertThat(provider.backendService(id("app-a")), notNullValue());

        provider.onChange(
                changeSetBuilder()
                        .added(backendB, backendC)
                        .build());

        assertThat(provider.backendService(id("app-a")), notNullValue());
        assertThat(provider.backendService(id("app-b")), notNullValue());
        assertThat(provider.backendService(id("app-c")), notNullValue());
    }

    @Test
    public void notifiesListenersAboutNewApps() {
        MockProvider registry = new MockProvider();
        Registry.ChangeListener<StyxBackendService> listener = mock(Registry.ChangeListener.class);

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);
        provider.addListener(listener);
        verify(listener).onChange(any(Registry.Changes.class));

        provider.onChange(
                changeSetBuilder()
                .added(backendA)
                .build());

        verify(listener, times(2)).onChange(captor.capture());

        List<StyxBackendService> addedBackends = newArrayList(captor.getValue().added());
        System.out.println("added backends: " + addedBackends);
        assertThat(addedBackends.get(0).httpClient(), is(instanceOf(HttpClient.class)));
        assertThat(addedBackends.get(0).originsInventory(), is(instanceOf(OriginsInventory.class)));
        assertThat(addedBackends.get(0).id(), is(backendA.id()));
        assertThat(addedBackends.get(0).configuration(), is(backendA));

        assertThat(addedBackends.size(), is(1));
        assertThat(captor.getValue().updated(), is(emptyIterable()));
        assertThat(captor.getValue().removed(), is(emptyIterable()));
    }

    @Test
    public void recreatesClientsWhenNetworkSettingsChange() {
        OriginsInventory inventory = mock(OriginsInventory.class);

        inventoryFactory = mock(OriginInventoryFactory.class);
        when(inventoryFactory.newInventory(any(BackendService.class)))
                .thenReturn(inventory);
        BackendService modifiedAppA = newBackendServiceBuilder(backendA)
                .connectionPoolConfig(
                        new ConnectionPoolSettings.Builder(defaultSettableConnectionPoolSettings())
                                .maxConnectionsPerHost(4)
                                .build())
                .build();

        when(clientFactory.createClient(eq(modifiedAppA), any(OriginsInventory.class))).thenReturn(clientA);

        MockProvider registry = new MockProvider();
        Registry.ChangeListener<StyxBackendService> listener = mock(Registry.ChangeListener.class);

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);
        provider.addListener(listener);

        provider.onChange(changeSetBuilder().added(backendA).build());
        provider.onChange(changeSetBuilder().updated(modifiedAppA).build());

        verify(inventory).close();
        verify(inventoryFactory).newInventory(eq(modifiedAppA));
        verify(clientFactory).createClient(eq(modifiedAppA), any(OriginsInventory.class));
    }

    @Test
    public void setsOriginsInventoryWhenOriginsChanged() throws Exception {
        OriginsInventory inventory = mock(OriginsInventory.class);
        Origin origin1 = newOriginBuilder("localhost", 8081).id("localhost-01").build();
        Origin origin2 = newOriginBuilder("localhost", 8082).id("localhost-02").build();

        inventoryFactory = mock(OriginInventoryFactory.class);
        when(inventoryFactory.newInventory(any(BackendService.class))).thenReturn(inventory);

        BackendService modifiedAppA = newBackendServiceBuilder(backendA)
                .origins(origin1, origin2)
                .build();

        when(clientFactory.createClient(eq(modifiedAppA), any(OriginsInventory.class))).thenReturn(clientA);

        MockProvider registry = new MockProvider();
        Registry.ChangeListener<StyxBackendService> listener = mock(Registry.ChangeListener.class);

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);
        provider.addListener(listener);

        provider.onChange(changeSetBuilder().added(backendA).build());
        provider.onChange(changeSetBuilder().updated(modifiedAppA).build());

        verify(inventory, never()).close();
        verify(inventoryFactory, never()).newInventory(eq(modifiedAppA));
        verify(clientFactory, never()).createClient(eq(modifiedAppA), any(OriginsInventory.class));

        verify(inventory).setOrigins(anySetOf(Origin.class));
    }

    @Test
    public void shutsClientAndInventoryWhenApplicationIsRemoved() {
        OriginsInventory inventory = mock(OriginsInventory.class);
        inventoryFactory = mock(OriginInventoryFactory.class);
        when(inventoryFactory.newInventory(any(BackendService.class))).thenReturn(inventory);

        MockProvider registry = new MockProvider();
        Registry.ChangeListener<StyxBackendService> listener = mock(Registry.ChangeListener.class);

        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);
        provider.addListener(listener);

        provider.onChange(changeSetBuilder().added(backendA).build());
        provider.onChange(changeSetBuilder().removed(backendA).build());

        verify(inventory).close();
    }

//    @Test
//    public void propagatesRemovalEventsAfterAdditions() {
//        MockProvider registry = new MockProvider();
//        Registry.ChangeListener<StyxBackendService> listener = mock(Registry.ChangeListener.class);
//
//        CommonBackendServiceRegistry provider = CommonBackendServiceRegistry.createAndListen(registry, inventoryFactory, clientFactory);
//        provider.addListener(listener);
//        verify(listener).onChange(any(Registry.Changes.class));
//
//        provider.onChange(
//                changeSetBuilder()
//                        .added(backendC)
//                        .removed(backendA)
//                        .build());
//
//        InOrder inOrder = Mockito.inOrder(listener);
//        inOrder.verify(listener).onChange();
//
//        verify(listener, times(2)).onChange(captor.capture());
//
//        List<StyxBackendService> addedBackends = newArrayList(captor.getValue().added());
//        System.out.println("added backends: " + addedBackends);
//        assertThat(addedBackends.get(0).httpClient(), is(instanceOf(HttpClient.class)));
//        assertThat(addedBackends.get(0).originsInventory(), is(instanceOf(OriginsInventory.class)));
//        assertThat(addedBackends.get(0).id(), is(backendA.id()));
//        assertThat(addedBackends.get(0).configuration(), is(backendA));
//
//        assertThat(addedBackends.size(), is(1));
//        assertThat(captor.getValue().updated(), is(emptyIterable()));
//        assertThat(captor.getValue().removed(), is(emptyIterable()));
//    }

    private Registry.Changes.Builder<BackendService> changeSetBuilder() {
        return new Registry.Changes.Builder<>();
    }

    private Registry.Changes.Builder<StyxBackendService> styxChangeSetBuilder() {
        return new Registry.Changes.Builder<>();
    }

    class MockProvider implements Registry<BackendService> {
        List<ChangeListener<BackendService>> listeners = new ArrayList<>();
        List<BackendService> backendServices = new ArrayList<>();

        @Override
        public Registry<BackendService> addListener(ChangeListener<BackendService> changeListener) {
            listeners.add(changeListener);
            return this;
        }

        @Override
        public Registry<BackendService> removeListener(ChangeListener<BackendService> changeListener) {
            listeners.remove(changeListener);
            return this;
        }

        @Override
        public CompletableFuture<ReloadResult> reload() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Iterable<BackendService> get() {
            return ImmutableList.copyOf(backendServices);
        }

        public void setBackends(List<BackendService> services) {
            Changes<BackendService> changes = changes(services, backendServices);

            if (!changes.isEmpty()) {
                backendServices = services;
                notifyListeners(changes);
            }
        }

        private void notifyListeners(Changes<BackendService> changes) {
            for (ChangeListener<BackendService> listener : listeners) {
                listener.onChange(changes);
            }
        }

        <T extends Identifiable> Changes<T> changes(Iterable<T> newResources, Iterable<T> currentResources) {
            Map<Id, T> newIdsToResource = mapById(newResources);
            Map<Id, T> currentIdsToResource = mapById(currentResources);

            MapDifference<Id, T> diff = difference(newIdsToResource, currentIdsToResource);

            Map<Id, MapDifference.ValueDifference<T>> diffs = diff.entriesDiffering();
            return new Changes.Builder<T>()
                    .added(diff.entriesOnlyOnLeft().values())
                    .removed(diff.entriesOnlyOnRight().values())
                    .updated(filterKeys(newIdsToResource, diffs::containsKey).values())
                    .build();
        }

        private <T extends Identifiable> Map<Id, T> mapById(Iterable<T> resources) {
            return stream(resources.spliterator(), false)
                    .collect(toMap(T::id, identity()));
        }

    }
}