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
package com.hotels.styx.client.loadbalancing.strategies;


import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.List;
import java.util.Random;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;


/**
 * A Power Of Two load balancing strategy.
 *
 * Randomly choose two origins, and return the one with less ongoing requests.
 */
public class PowerOfTwoStrategy implements LoadBalancingStrategy {
    private ActiveOrigins activeOrigins;
    private Random rng;

    public PowerOfTwoStrategy(ActiveOrigins activeOrigins) {
        this(activeOrigins, new Random());
    }

    @VisibleForTesting
    PowerOfTwoStrategy(ActiveOrigins activeOrigins, Random rng) {
        this.activeOrigins = activeOrigins;
        this.rng = rng;
    }

    /**
     * A load balancing strategy that favours the origin with the least response time.
     */
    public static class Factory implements LoadBalancingStrategyFactory {
        @Override
        public LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new PowerOfTwoStrategy(activeOrigins);
        }
    }

    @Override
    public Iterable<RemoteHost> vote(Context context) {
        List<ConnectionPoolStatus> poolsList = stream(activeOrigins.snapshot().spliterator(), false)
                .map(host -> new ConnectionPoolStatus(host, context))
                .collect(toList());

        if (poolsList.size() == 0) {
            return ImmutableList.of();
        }

        if (poolsList.size() == 1) {
            return ImmutableList.of(poolsList.get(0).host());
        }

        ConnectionPoolStatus poolStatus1 = choose(poolsList);
        ConnectionPoolStatus poolStatus2 = choose(poolsList, poolStatus1);

        return betterOf(poolStatus1, poolStatus2);
    }

    private Iterable<RemoteHost> betterOf(ConnectionPoolStatus poolStatus1, ConnectionPoolStatus poolStatus2) {
        int metric1 = poolStatus1.busyConnectionCount() + poolStatus1.pendingConnectionCount();
        int metric2 = poolStatus2.busyConnectionCount() + poolStatus2.pendingConnectionCount();

        if (metric1 < metric2) {
            return ImmutableList.of(poolStatus1.host(), poolStatus2.host());
        } else {
            return ImmutableList.of(poolStatus2.host(), poolStatus1.host());
        }
    }

    private ConnectionPoolStatus choose(List<ConnectionPoolStatus> poolsList, ConnectionPoolStatus another) {
        int i = 0;
        ConnectionPoolStatus second = choose(poolsList);

        while (i < 20 && another.host().id() == second.host.id()) {
            second = choose(poolsList);
            i++;
        }

        return second;
    }

    private ConnectionPoolStatus choose(List<ConnectionPoolStatus> poolsList) {
        int index = rng.nextInt(poolsList.size());
        return poolsList.get(index);
    }

    private static class ConnectionPoolStatus {
        private final RemoteHost host;
        private final int busyConnectionCount;
        private final int pendingConnectionCount;

        public ConnectionPoolStatus(RemoteHost host, Context context) {
            this.host = host;
            this.busyConnectionCount = host.connectionPool().stats().busyConnectionCount();
            this.pendingConnectionCount = host.connectionPool().stats().pendingConnectionCount();
        }

        public int busyConnectionCount() {
            return busyConnectionCount;
        }

        public int pendingConnectionCount() {
            return pendingConnectionCount;
        }

        public ConnectionPool pool() {
            return host.connectionPool();
        }

        public RemoteHost host() {
            return host;
        }
    }
}
