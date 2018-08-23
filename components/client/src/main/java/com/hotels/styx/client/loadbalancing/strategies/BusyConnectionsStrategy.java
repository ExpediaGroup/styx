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
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.shuffle;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;


/**
 * A load balancing strategy that returns the origin with least ongoing connections.
 */
public class BusyConnectionsStrategy implements LoadBalancer {
    private final ActiveOrigins activeOrigins;

    public BusyConnectionsStrategy(ActiveOrigins activeOrigins) {
        this.activeOrigins = requireNonNull(activeOrigins);
    }

    /**
     * A load balancing strategy that favours the origin with the least response time.
     */
    public static class Factory implements LoadBalancerFactory {
        @Override
        public LoadBalancer create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new BusyConnectionsStrategy(activeOrigins);
        }
    }

    @Override
    public Optional<RemoteHost> choose(LoadBalancer.Preferences preferences) {
        List<RemoteHost> snapshot = stream(activeOrigins.snapshot().spliterator(), false)
                .collect(Collectors.toList());

        shuffle(snapshot);

        return snapshot.stream()
                .min(comparingInt(host -> host.metric().ongoingConnections()));
    }
}
