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

import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import com.hotels.styx.api.extension.service.spi.Registry;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class RegistryServiceAdapter extends AbstractStyxService implements Registry<BackendService> {
    private Registry<BackendService> delegate;

    public RegistryServiceAdapter(Registry<BackendService> delegate) {
        super("Memory backed backend service registry");
        this.delegate = requireNonNull(delegate);
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
        return delegate.reload();
    }

    @Override
    public Iterable<BackendService> get() {
        return delegate.get();
    }
}
