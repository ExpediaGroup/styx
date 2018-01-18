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

import com.hotels.styx.api.Id;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.infrastructure.Registry;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * To be done.
 */
public class BackendServicesRegistry implements Registry.ChangeListener<BackendService> {
    private final ConcurrentHashMap<Id, RegisteredBackend> backends;
    private Registry<BackendService> provider;
    private OriginInventoryFactory originInventoryFactory;

    private BackendServicesRegistry(Registry<BackendService> provider, OriginInventoryFactory originInventoryFactory) {
        this.provider = requireNonNull(provider);
        this.originInventoryFactory = requireNonNull(originInventoryFactory);
        this.backends = new ConcurrentHashMap<>();
    }

    public static BackendServicesRegistry createAndListen(Registry<BackendService> provider, OriginInventoryFactory originInventoryFactory) {
        BackendServicesRegistry registry = new BackendServicesRegistry(provider, originInventoryFactory);
        registry.start();
        return registry;
    }

    private void start() {
        /*
         * To avoid concurrency issues, BackendServiceRegistry must first establish its initial state before
         * registering for change notifications.
         *
         * TODO: It is now possible that we will miss notifications from provider when they occur
         * between provider.get() and provider.addListener() calls.
         *
         * As for the concurrency issues. It is probably better to pass the BackendServicesRegistry
         * as a constructor argument for the SPI provider object. This will, however result in backwards
         * compatibility issues (with BONO team).
         */
        Iterable<BackendService> backendServices = provider.get();
        provider.addListener(this);
        backendServices.forEach(this::addService);
    }

    private void addService(BackendService newService) {
        OriginHealthStatusMonitor healthMonitor = createHealthMonitor();
        OriginsInventory inventory = createInventory(newService);
        RegisteredBackend backend = new RegisteredBackend(newService, inventory, healthMonitor);
        backends.put(newService.id(), backend);
    }

    private void modifyService(BackendService modified) {
        Id id = modified.id();

        RegisteredBackend current = this.backends.get(id);

        current.inventory.setOrigins();
    }

    private OriginsInventory createInventory(BackendService backendService) {
        return originInventoryFactory.newInventory(backendService);
    }

    private OriginHealthStatusMonitor createHealthMonitor() {
        return null;
    }

    /**
     * Notifies BackendServicesRegistry on changes to BackendServices.
     *
     * The called ensures that backend service IDs are unique. When the changeset contains
     * multiple instances of the same ID, then the behaviour is unspecified. In that case the
     * backend service may be modified, removed, or it may be re-created.
     *
     * @param changes
     */
    @Override
    public void onChange(Registry.Changes<BackendService> changes) {
        changes.added().forEach(this::addService);
        changes.updated().forEach(this::modifyService);
    }

    @Override
    public void onError(Throwable ex) {

    }

    public OriginsInventory originsFor(Id appId) {
        RegisteredBackend backend = backends.get(appId);
        if (backend == null) {
            return null;
        }
        return backend.inventory;
    }

    private static class RegisteredBackend {
        private final BackendService newService;
        private final OriginsInventory inventory;
        private final OriginHealthStatusMonitor healthMonitor;

        public RegisteredBackend(BackendService newService, OriginsInventory inventory, OriginHealthStatusMonitor healthMonitor) {
            this.newService = newService;
            this.inventory = inventory;
            this.healthMonitor = healthMonitor;
        }
    }
}
