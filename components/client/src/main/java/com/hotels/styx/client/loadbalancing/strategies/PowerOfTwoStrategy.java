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


import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.Optional;
import java.util.Random;

import static com.google.common.collect.Iterables.toArray;
import static java.util.Objects.requireNonNull;


/**
 * A load balancing strategy that sorts origins according to three functions:
 * <p>
 * Whether they have below or above average 5xx errors.
 * The number of busy connections.
 * Whether they having existing connections available.
 */
public class PowerOfTwoStrategy implements LoadBalancer {
    private final ActiveOrigins activeOrigins;
    private final Random rng;

    @VisibleForTesting
    PowerOfTwoStrategy(ActiveOrigins activeOrigins, Random rng) {
        this.activeOrigins = requireNonNull(activeOrigins);
        this.rng = requireNonNull(rng);
    }

    public PowerOfTwoStrategy(ActiveOrigins activeOrigins) {
        this(activeOrigins, new Random());
    }

    /**
     * A load balancing strategy that favours the origin with the least response time.
     */
    public static class Factory implements LoadBalancerFactory {
        @Override
        public LoadBalancer create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new PowerOfTwoStrategy(activeOrigins);
        }
    }

    @Override
    public Optional<RemoteHost> choose(LoadBalancer.Preferences preferences) {
        RemoteHost[] hosts = toArray(activeOrigins.snapshot(), RemoteHost.class);

        if (hosts.length == 0) {
            return Optional.empty();
        } else if (hosts.length == 1) {
            return Optional.of(hosts[0]);
        } else {
            int i1 = rng.nextInt(hosts.length);
            int i2 = drawFromRemaining(hosts.length, i1);

            return Optional.of(betterOf(hosts[i1], hosts[i2]));
        }
    }

    private int drawFromRemaining(int bound, int otherIndex) {
        int i = rng.nextInt(bound - 1);
        return (i < otherIndex) ? i : i + 1;
    }

    private RemoteHost betterOf(RemoteHost host1, RemoteHost host2) {
        return host1.metric().ongoingConnections() < host2.metric().ongoingConnections() ? host1 : host2;
    }

}
