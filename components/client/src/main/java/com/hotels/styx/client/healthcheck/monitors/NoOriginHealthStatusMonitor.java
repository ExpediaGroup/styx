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
package com.hotels.styx.client.healthcheck.monitors;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * An {@link com.hotels.styx.client.healthcheck.OriginHealthStatusMonitor} that does nothing. Using this monitor is
 * a way to disable monitoring.
 */
public class NoOriginHealthStatusMonitor implements OriginHealthStatusMonitor {
    @Override
    public CompletableFuture<Void> start() {
        return completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        return completedFuture(null);
    }

    @Override
    public OriginHealthStatusMonitor monitor(Set<Origin> origins) {
        return this;
    }

    @Override
    public OriginHealthStatusMonitor stopMonitoring(Set<Origin> origins) {
        return this;
    }

    @Override
    public OriginHealthStatusMonitor addOriginStatusListener(OriginHealthStatusMonitor.Listener listener) {
        return this;
    }
}
