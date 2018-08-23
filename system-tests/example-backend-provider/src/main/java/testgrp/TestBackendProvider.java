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
package testgrp;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.extension.service.spi.AbstractRegistry;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.reloaded;
import static com.hotels.styx.api.extension.service.spi.Registry.ReloadResult.unchanged;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static testgrp.AdminHandlers.adminHandlers;


/**
 * An example implementation of Backend Service Provider for styx
 * <p>
 * - Extends AbstractStyxService to get default styx service lifecycle implementation
 * - Implement Registry\<BackendService\>
 */
public class TestBackendProvider extends AbstractStyxService implements Registry<BackendService> {

    private final MyRegistry delegate;

    private TestBackendProvider(BackendService service) {
        super(service.id().toString());
        this.delegate = new MyRegistry();
        this.delegate.set(singletonList(service));
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return adminHandlers("x", "y");
    }

    @Override
    public Registry<BackendService> addListener(ChangeListener<BackendService> changeListener) {
        return delegate.addListener(changeListener);
    }

    @Override
    public Registry<BackendService> removeListener(ChangeListener<BackendService> changeListener) {
        return delegate.removeListener(changeListener);
    }

    @Override
    public CompletableFuture<ReloadResult> reload() {
        return completedFuture(reloaded("all ok"));
    }

    @Override
    public Iterable<BackendService> get() {
        return delegate.get();
    }

    private class MyRegistry extends AbstractRegistry<BackendService> {
        @Override
        public CompletableFuture<ReloadResult> reload() {
            return completedFuture(unchanged("Nothing changed"));
        }
    }

    /**
     * Factory for creating a {@link Registry}.
     */
    public static class Factory implements Registry.Factory<BackendService> {

        @Override
        public Registry<BackendService> create(Environment environment, Configuration registryConfiguration) {
            BackendService service = registryConfiguration.get("backendService", BackendService.class)
                    .orElseThrow(() -> new ConfigurationException(
                            "missing [services.registry.factory.config.backendService] config value for factory class TestBackendProvider.Factory"));

            return new TestBackendProvider(service);
        }
    }

}
