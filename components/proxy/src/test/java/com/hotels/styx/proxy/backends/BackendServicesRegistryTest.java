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
import com.google.common.collect.MapDifference;
import com.hotels.styx.Environment;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.infrastructure.Registry;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.filterKeys;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
import static java.lang.Thread.currentThread;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BackendServicesRegistryTest {

    private Environment environment = new Environment.Builder().build();
    private OriginInventoryFactory inventoryFactory = new OriginInventoryFactory(environment, 10);
    private BackendService backendA;
    private BackendService backendB;
    private BackendService backendC;

    @BeforeMethod
    public void setUp() {
        backendA = newBackendServiceBuilder().id("a").origins(newOriginBuilder("localhost", 8080).build()).build();
        backendB = newBackendServiceBuilder().id("b").origins(newOriginBuilder("localhost", 8080).build()).build();
        backendC = newBackendServiceBuilder().id("c").origins(newOriginBuilder("localhost", 8080).build()).build();
    }

    @Test
    public void listensToProviderOnlyAfterInitialSetup() {
        /*
         * To avoid concurrency issues, BackendServiceRegistry must first establish its initial state before
         * registering for change notifications.
         */
        Registry<BackendService> provider = mock(Registry.class);
        when(provider.get()).thenReturn(ImmutableList.of());

        BackendServicesRegistry registry = BackendServicesRegistry.createAndListen(provider, inventoryFactory);

        InOrder inOrder = inOrder(provider);
        inOrder.verify(provider).get();
        inOrder.verify(provider).addListener(eq(registry));
    }

    @Test
    public void populatesOriginInventoriesDuringStartup() {
        MockProvider provider = new MockProvider();
        provider.setBackends(ImmutableList.of(backendA, backendB, backendC));

        BackendServicesRegistry registry = BackendServicesRegistry.createAndListen(provider, inventoryFactory);

        assertThat(registry.originsFor(id("a")), instanceOf(OriginsInventory.class));
        assertThat(registry.originsFor(id("b")), instanceOf(OriginsInventory.class));
        assertThat(registry.originsFor(id("c")), instanceOf(OriginsInventory.class));
    }

    @Test
    public void addsNewOriginsFromOnChangeNotification() {
        MockProvider provider = new MockProvider();

        BackendServicesRegistry registry = BackendServicesRegistry.createAndListen(provider, inventoryFactory);

        registry.onChange(
                changeSetBuilder()
                        .added(backendA)
                        .build());

        assertThat(registry.originsFor(id("a")), notNullValue());

        registry.onChange(
                changeSetBuilder()
                        .added(backendB, backendC)
                        .build());

        assertThat(registry.originsFor(id("a")), notNullValue());
        assertThat(registry.originsFor(id("b")), notNullValue());
        assertThat(registry.originsFor(id("c")), notNullValue());
    }


//    @Test
//    public void updatesOriginsFromOnChangeNotification() {
//        MockProvider provider = new MockProvider();
//
//        BackendServicesRegistry registry = BackendServicesRegistry.createAndListen(provider, inventoryFactory);
//
//        registry.onChange(
//                changeSetBuilder()
//                        .added(backendA)
//                        .build());
//
//        assertThat(registry.originsFor(id("a")), notNullValue());
//
//        registry.onChange(
//                changeSetBuilder()
//                        .updated(backendA.newBackendServiceBuilder()
//                                .path("/y")
//                                .origins(newOriginBuilder("localhost", 8081).build())
//                                .build())
//                        .build());
//
//        OriginsInventory inventory = registry.originsFor(id("a"));
//
//        assertThat(inventory.origins().size(), Matchers.is(1));
//        assertThat(inventory.origins().get(0).hostAsString(), Matchers.is("localhost:8081"));
//    }

    private List<Origin> asList(Iterable<ConnectionPool> snapshot) {
        return null;
    }


    private Registry.Changes.Builder<BackendService> changeSetBuilder() {
        return new Registry.Changes.Builder<>();
    }

//    @Test
//    public void handlesSimultaneousNotificationsDuringStartup() throws Exception {
//        for (int j = 0; j < 100; j++) {
//            final AtomicReference<BackendServicesRegistry> registry = new AtomicReference<>();
//
//            MockProvider provider = new MockProvider();
//
//            Thread thread1 = new Thread(() -> {
//                registry.set(BackendServicesRegistry.createAndListen(provider, inventoryFactory));
//            });
//
//            Thread thread2 = new Thread(() -> {
//                ImmutableList.Builder<BackendService> builder = new ImmutableList.Builder<>();
//                for (int i = 0; i < 1000; i++) {
//                    BackendService host = newBackendServiceBuilder().id(format("%d", i)).origins(Origin.newOriginBuilder("host", 8080).build()).build();
//                    builder.add(host);
//                    provider.setBackends(builder.build());
//                }
//            });
//
//            thread1.start();
//            thread2.start();
//
//            thread1.join();
//            thread2.join();
//
//            BackendServicesRegistry r = registry.get();
//            for (int i = 0; i < 1000; i++) {
////                System.out.println(format("checking for i=%d", i));
//                assertThat(r.originsFor(id(format("%d", i))), notNullValue());
//            }
//        }
//    }

    private static Thread newThread(Runnable runnable) {
        return new Thread(runnable, "test-thread");
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            currentThread().interrupt();
            ;
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
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