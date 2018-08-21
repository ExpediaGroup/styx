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
package com.hotels.styx.infrastructure;

import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.api.extension.service.BackendService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MemoryBackedRegistryTest {
    Registry.ChangeListener<BackendService> listener;

    @BeforeMethod
    public void createMockListener() {
        listener = mock(Registry.ChangeListener.class);
    }

    @Test
    public void addsResources() {
        BackendService landing = backendService("landing", 9091);
        BackendService shopping = backendService("shopping", 9090);

        MemoryBackedRegistry<BackendService> registry = new MemoryBackedRegistry<>();
        registry.add(landing);
        registry.addListener(listener);

        registry.add(shopping);

        assertThat(registry.get(), containsInAnyOrder(landing, shopping));
        verify(listener).onChange(eq(added(shopping)));
    }

    @Test
    public void updatesResources() {
        BackendService landing = backendService("landing", 9091);

        MemoryBackedRegistry<BackendService> registry = new MemoryBackedRegistry<>();
        registry.add(backendService("shopping", 9090));
        registry.add(landing);

        registry.addListener(listener);

        BackendService shopping = backendService("shopping", 9091);
        registry.add(shopping);

        assertThat(registry.get(), containsInAnyOrder(landing, shopping));
        verify(listener).onChange(eq(updated(shopping)));
    }

    @Test
    public void removesResources() {
        BackendService shopping = backendService("shopping", 9090);
        BackendService landing = backendService("landing", 9091);

        MemoryBackedRegistry<BackendService> registry = new MemoryBackedRegistry<>();

        registry.add(shopping);
        registry.add(landing);

        registry.addListener(listener);

        registry.removeById(id("shopping"));

        assertThat(registry.get(), contains(landing));
        verify(listener).onChange(eq(removed(shopping)));
    }

    private static BackendService backendService(String id, int port) {
        return new BackendService.Builder()
                .id(id)
                .origins(newOriginBuilder("localhost", port).build())
                .build();
    }

    private <T extends Identifiable> Registry.Changes<T> added(T... ts) {
        return new Registry.Changes.Builder<T>()
                .added(ts)
                .build();
    }

    private <T extends Identifiable> Registry.Changes<T> updated(T... ts) {
        return new Registry.Changes.Builder<T>()
                .updated(ts)
                .build();
    }

    private <T extends Identifiable> Registry.Changes<T> removed(T... ts) {
        return new Registry.Changes.Builder<T>()
                .removed(ts)
                .build();
    }
}