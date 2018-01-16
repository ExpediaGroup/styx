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
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

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
public class RoundRobinStrategy implements LoadBalancingStrategy {

    private final ActiveOrigins activeOrigins;

    public RoundRobinStrategy(ActiveOrigins activeOrigins) {
        this.activeOrigins = activeOrigins;
    }

    /**
     * Factory for creating {@link com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy}.
     */
    public static class Factory implements LoadBalancingStrategyFactory {
        @Override
        public LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return new RoundRobinStrategy(activeOrigins);
        }
    }

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Iterable<ConnectionPool> vote(Context context) {
        Iterable<ConnectionPool> snapshot = activeOrigins.snapshot();
        return isEmpty(snapshot) ? snapshot : cycledNonExhaustedOrigins(snapshot);
    }

    private List<ConnectionPool> cycledNonExhaustedOrigins(Iterable<ConnectionPool> origins) {
        return cycleOrigins(origins)
                .filter(pool -> !pool.isExhausted())
                .collect(toList());
    }

    @Override
    public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        index.set(0);
    }

    private Stream<ConnectionPool> cycleOrigins(Iterable<ConnectionPool> origins) {
        List<ConnectionPool> originsList = newArrayList(origins);
        return cycleToOffset(nextIndex(originsList), originsList);
    }

    private int nextIndex(List<ConnectionPool> origins) {
        return index.getAndIncrement() % origins.size();
    }

    private static Stream<ConnectionPool> cycleToOffset(int index, List<ConnectionPool> origins) {
        List<ConnectionPool> first = origins.subList(index, origins.size());
        List<ConnectionPool> second = origins.subList(0, index);
        return concat(first.stream(), second.stream());
    }

    @Override
    public String toString() {
        return getClass().toString();
    }
}
