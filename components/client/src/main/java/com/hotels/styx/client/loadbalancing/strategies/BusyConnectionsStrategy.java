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


import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.Comparator;
import java.util.List;

import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;


/**
 * A load balancing strategy that sorts origins according to three functions:
 * <p>
 * Whether they have below or above average 5xx errors.
 * The number of busy connections.
 * Whether they having existing connections available.
 */
public class BusyConnectionsStrategy implements LoadBalancingStrategy {
    private ActiveOrigins activeOrigins;

    public BusyConnectionsStrategy(ActiveOrigins activeOrigins) {
        this.activeOrigins = activeOrigins;
    }

    /**
     * A load balancing strategy that favours the origin with the least response time.
     */
    public static class Factory implements LoadBalancingStrategyFactory {
        @Override
        public LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new BusyConnectionsStrategy(activeOrigins);
        }
    }

    @Override
    public Iterable<RemoteHost> vote(Context context) {
        List<ConnectionPoolStatus> poolsList = stream(activeOrigins.snapshot().spliterator(), false)
                .map(host -> new ConnectionPoolStatus(host, context))
                .collect(toList());

        double average5xxRate = average5xxRate(poolsList);

        shuffle(poolsList);

        poolsList.sort(byComparing(
                (first, second) -> Integer.compare(first.busyConnectionCount(), second.busyConnectionCount()),
                (first, second) -> Integer.compare(first.pendingConnectionCount(), second.pendingConnectionCount()),
                (first, second) -> Boolean.compare(second.availableConnectionCount() > 0, first.availableConnectionCount() > 0)
        ));

        return poolsList.stream()
                .map(ConnectionPoolStatus::host)
                .collect(toList());
    }

    private static Comparator<ConnectionPoolStatus> byComparing(Comparator<ConnectionPoolStatus> first, Comparator<ConnectionPoolStatus>... rest) {
        Comparator<ConnectionPoolStatus> chain = first;
        for (Comparator<ConnectionPoolStatus> comparator : rest) {
            chain = chain.thenComparing(comparator);
        }
        return chain;
    }

    private static double average5xxRate(Iterable<ConnectionPoolStatus> pools) {
        return stream(pools.spliterator(), false)
                .mapToDouble(ConnectionPoolStatus::status5xxRate)
                .average()
                .orElse(0.0);
    }

    private static class ConnectionPoolStatus {
        private final RemoteHost host;
        private final double status5XxRate;
        private final int availableCount;
        private final int busyConnectionCount;
        private final int pendingConnectionCount;

        public ConnectionPoolStatus(RemoteHost host, Context context) {
            this.host = host;
            this.status5XxRate = context.oneMinuteRateForStatusCode5xx(host.origin());
            this.availableCount = host.connectionPool().stats().availableConnectionCount();
            this.busyConnectionCount = host.connectionPool().stats().busyConnectionCount();
            this.pendingConnectionCount = host.connectionPool().stats().pendingConnectionCount();
        }

        public double status5xxRate() {
            return status5XxRate;
        }

        public int availableConnectionCount() {
            return availableCount;
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
