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

import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;
import com.hotels.styx.infrastructure.AbstractRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.proxy.BackendServiceClientFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.hotels.styx.proxy.backends.BackendServiceSupport.clientConfigurationChanged;
import static com.hotels.styx.proxy.backends.BackendServiceSupport.networkConfigurationChanged;
import static com.hotels.styx.proxy.backends.BackendServiceSupport.originConfigurationChanged;
import static java.util.Objects.requireNonNull;

/**
 * To be done.
 */
public class CommonBackendServiceRegistry extends AbstractRegistry<CommonBackendServiceRegistry.StyxBackendService> implements Registry.ChangeListener<BackendService> {
    private final ConcurrentHashMap<Id, StyxBackendService> backends;
    private final OriginInventoryFactory originInventoryFactory;
    private Registry<BackendService> provider;
    private BackendServiceClientFactory clientFactory;

    private CommonBackendServiceRegistry(Registry<BackendService> provider, OriginInventoryFactory originInventoryFactory, BackendServiceClientFactory clientFactory) {
        this.provider = requireNonNull(provider);
        this.originInventoryFactory = requireNonNull(originInventoryFactory);
        this.clientFactory = requireNonNull(clientFactory);
        this.backends = new ConcurrentHashMap<>();
    }

    public static CommonBackendServiceRegistry createAndListen(
            Registry<BackendService> provider,
            OriginInventoryFactory originInventoryFactory,
            BackendServiceClientFactory clientFactory
    ) {
        CommonBackendServiceRegistry registry = new CommonBackendServiceRegistry(provider, originInventoryFactory, clientFactory);
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
         * compatibility issues other teams.
         */
        Iterable<BackendService> backendServices = provider.get();
        provider.addListener(this);
        backendServices.forEach(this::addService);
    }

    private void addService(BackendService newService) {
        OriginsInventory inventory = originInventoryFactory.newInventory(newService);
        HttpClient httpClient = clientFactory.createClient(newService, inventory);

        StyxBackendService backend = new StyxBackendService(inventory, httpClient, newService);
        backends.put(newService.id(), backend);
        set(ImmutableSet.copyOf(backends.values()));
    }

    private void removeService(StyxBackendService current) {
        current.originsInventory().close();

    }

    private void modifyService(BackendService modified) {
        Id id = modified.id();
        StyxBackendService current = this.backends.get(id);
        current.originsInventory.setOrigins(modified.origins());
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

        changes.updated().forEach(service -> {
            StyxBackendService current = backends.get(service.id());
            if (networkConfigurationChanged(current.configuration(), service)
                    || clientConfigurationChanged(current.configuration(), service)) {
                current.originsInventory().close();
                backends.remove(service.id());
                addService(service);
            } else if (originConfigurationChanged(current.configuration(), service)) {
                current.originsInventory().setOrigins(service.origins());
            }
            // Health check configuration or path prefix changed.
            // For now, do nothing.

        });

        changes.removed().forEach(service -> {
            StyxBackendService current = backends.get(service.id());
            current.originsInventory().close();
            backends.remove(service.id());
        });
    }

    @Override
    public void onError(Throwable ex) {

    }

    public StyxBackendService backendService(Id appId) {
        StyxBackendService backend = backends.get(appId);
        if (backend == null) {
            return null;
        }
        return backend;
    }

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return CompletableFuture.completedFuture(ReloadResult.reloaded("ok"));
    }

    /**
     * Styx backend service representation.
     */
    public static class StyxBackendService implements Identifiable {
        private OriginsInventory originsInventory;
        private HttpClient httpClient;
        private BackendService configuration;

        public StyxBackendService(OriginsInventory originsInventory, HttpClient httpClient, BackendService configuration) {
            this.originsInventory = requireNonNull(originsInventory);
            this.httpClient = requireNonNull(httpClient);
            this.configuration = requireNonNull(configuration);
        }

        @Override
        public Id id() {
            return this.configuration.id();
        }

        public HttpClient httpClient() {
            return httpClient;
        }

        public OriginsInventory originsInventory() {
            return originsInventory;
        }

        public BackendService configuration() {
            return configuration;
        }
    }

}
