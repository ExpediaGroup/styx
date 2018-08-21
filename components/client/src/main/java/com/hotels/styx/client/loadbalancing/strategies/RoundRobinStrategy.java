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
package com.hotels.styx.client.loadbalancing.strategies;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;

/**
 * A load balancing strategy that favours all origins equally, iterating through them in the order that they are provided.
 * Provided that the origins provided to the vote method are the same as the previous call, the returned
 * origins will be in the same order, but the previous first origin will have been moved back to the end
 * and the previous second origin will now be the first.
 * <p/>
 * e.g.
 * <p/>
 * Call 1:  Origin A, Origin B, Origin C
 * Call 2:  Origin B, Origin C, Origin A
 * Call 3:  Origin C, Origin A, Origin B
 * <p/>
 * Please note that for the strategy to iterate like this, the origins must be provided in the same order on each call.
 */
public class RoundRobinStrategy implements LoadBalancer {

    private ActiveOrigins activeOrigins;
    private final AtomicReference<ArrayList<RemoteHost>> origins;
    private final AtomicInteger index = new AtomicInteger(0);

    public RoundRobinStrategy(ActiveOrigins activeOrigins, Iterable<RemoteHost> initialOrigins) {
        this.activeOrigins = requireNonNull(activeOrigins);
        this.origins = new AtomicReference<>(new ArrayList<>(newArrayList(initialOrigins)));
    }

    /**
     * Factory for creating {@link com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy}.
     */
    public static class Factory implements LoadBalancerFactory {
        @Override
        public LoadBalancer create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new RoundRobinStrategy(activeOrigins, activeOrigins.snapshot());
        }
    }

    @Override
    public Optional<RemoteHost> choose(Preferences preferences) {
        ArrayList<RemoteHost> remoteHosts = origins.get();
        return Optional.ofNullable(remoteHosts.get(index.getAndIncrement() % remoteHosts.size()));
    }

    @Override
    public void originsChanged(OriginsSnapshot snapshot) {
        origins.set(newArrayList(activeOrigins.snapshot()));
    }

    @Override
    public String toString() {
        return getClass().toString();
    }
}
