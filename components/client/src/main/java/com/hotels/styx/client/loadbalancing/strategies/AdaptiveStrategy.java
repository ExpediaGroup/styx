/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
import com.google.common.util.concurrent.AtomicDouble;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.Configuration;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.common.MorePreconditions.checkArgument;

/**
 * A load balancing strategy that starts as round robin at first, then switches to busy connections after the number
 * of times it has been called reaches a warmup count.
 */
public class AdaptiveStrategy implements LoadBalancingStrategy {
    public static final int DEFAULT_REQUEST_COUNT = 100;

    /**
     * Factory for creating the {@link com.hotels.styx.client.loadbalancing.strategies.AdaptiveStrategy} using configured
     * settings.
     */
    public static class Factory implements LoadBalancingStrategyFactory {
        @Override
        public AdaptiveStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            int requestCount = strategyConfiguration.get("requestCount", Integer.class)
                    .orElse(DEFAULT_REQUEST_COUNT);
            return new AdaptiveStrategy(requestCount, activeOrigins, new RoundRobinStrategy(activeOrigins),
                    new BusyConnectionsStrategy(activeOrigins));
        }
    }

    private final AtomicDouble borrowCount = new AtomicDouble(0);

    private ActiveOrigins activeOrigins;
    private final RoundRobinStrategy roundRobin;
    private final BusyConnectionsStrategy leastResponseTime;
    private final int requestCount;
    private volatile LoadBalancingStrategy currentStrategy;

    public AdaptiveStrategy(ActiveOrigins activeOrigins) {
        this(DEFAULT_REQUEST_COUNT, activeOrigins,
                new RoundRobinStrategy(activeOrigins),
                new BusyConnectionsStrategy(activeOrigins));
    }

    public AdaptiveStrategy(int requestCount, ActiveOrigins activeOrigins, RoundRobinStrategy roundRobin,
                            BusyConnectionsStrategy leastResponseTime) {
        this.requestCount = checkArgument(requestCount, requestCount > 0);
        this.activeOrigins = activeOrigins;
        this.roundRobin = checkNotNull(roundRobin);
        this.leastResponseTime = checkNotNull(leastResponseTime);

        setCurrentStrategyToRoundRobin();
    }

    @VisibleForTesting
    int requestCount() {
        return requestCount;
    }

    private void setCurrentStrategyToRoundRobin() {
        this.borrowCount.set(0.0);
        this.currentStrategy = this.roundRobin;
    }

    @Override
    public Iterable<ConnectionPool> vote(Context context) {
        int nOrigins = size(activeOrigins.snapshot());
        if (nOrigins < 2) {
            return activeOrigins.snapshot();
        }

        if (this.currentStrategy == this.roundRobin) {
            double currentBorrowCount = borrowCount.getAndAdd(1.0 / (double) nOrigins);
            if (currentBorrowCount >= requestCount) {
                this.currentStrategy = this.leastResponseTime;
            }
        }

        return this.currentStrategy.vote(context);
    }

    @Override
    public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        setCurrentStrategyToRoundRobin();
    }

    public LoadBalancingStrategy currentStrategy() {
        return currentStrategy;
    }
}
