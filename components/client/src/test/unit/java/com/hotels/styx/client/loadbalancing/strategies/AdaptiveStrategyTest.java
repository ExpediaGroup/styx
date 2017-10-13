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

import com.google.common.collect.Iterables;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AdaptiveStrategyTest {
    public static final Origin SOME_ORIGIN = newOriginBuilder("localhost", 1).build();
    public static final Origin ANOTHER_ORIGIN = newOriginBuilder("localhost", 2).build();
    public static final int WARMUP_COUNT = 10;
    public static final LoadBalancingStrategy.Context UNUSED_CONTEXT = null;

    RoundRobinStrategy roundRobinStrategy;
    BusyConnectionsStrategy busyConnectionsStrategy;
    AdaptiveStrategy adaptiveStrategy;
    ConnectionPool origin1;
    ConnectionPool origin2;

    @BeforeMethod
    public void setUp() {
        roundRobinStrategy = mock(RoundRobinStrategy.class);
        busyConnectionsStrategy = mock(BusyConnectionsStrategy.class);
        adaptiveStrategy = new AdaptiveStrategy(WARMUP_COUNT, roundRobinStrategy, busyConnectionsStrategy);

        origin1 = aConnectionPool(SOME_ORIGIN);
        origin2 = aConnectionPool(ANOTHER_ORIGIN);
    }

    @Test
    public void startsWithRoundRobinStrategy() {
        assertThat(adaptiveStrategy.currentStrategy(), is((LoadBalancingStrategy) roundRobinStrategy));
    }

    @Test
    public void willHandleEmptyOrSingleOrigin() {
        Iterable<ConnectionPool> vote = adaptiveStrategy.vote(EMPTY_LIST, null);
        assertThat(vote, is(emptyIterable()));

        Iterable<ConnectionPool> single = adaptiveStrategy.vote(singletonList(origin1), null);
        assertThat(Iterables.size(single), is(1));
    }

    @Test
    public void switchesToBusyConnectionsStrategyAfterConfiguredNumberOfRequestsForEachOrigin() {
        adaptiveStrategy = new AdaptiveStrategy(10, roundRobinStrategy, busyConnectionsStrategy);

        exerciseStrategy(adaptiveStrategy, origin1, origin2);

        verify(roundRobinStrategy, times(1)).vote(anyCollection(), anyContext());
        verify(busyConnectionsStrategy, times(0)).vote(anyCollection(), anyContext());

        for (int i = 0; i < 19; i++) {
            exerciseStrategy(adaptiveStrategy, origin1, origin2);
        }

        verify(roundRobinStrategy, times(20)).vote(anyCollection(), anyContext());
        verify(busyConnectionsStrategy, times(0)).vote(anyCollection(), anyContext());

        exerciseStrategy(adaptiveStrategy, origin1, origin2);

        assertThat(adaptiveStrategy.currentStrategy(), is(busyConnectionsStrategy));
        verify(roundRobinStrategy, times(20)).vote(anyCollection(), anyContext());
        verify(busyConnectionsStrategy, times(1)).vote(anyCollection(), anyContext());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwsExceptionIfInitialisedWithWarmUpLessThanOne() {
        new AdaptiveStrategy(0, roundRobinStrategy, busyConnectionsStrategy);
    }

    @Test
    public void switchesBackToRoundRobinOnInventoryStateChanges() {
        AdaptiveStrategy strategy = exerciseStrategy(new AdaptiveStrategy(1, roundRobinStrategy, busyConnectionsStrategy), origin1, origin2);
        exerciseStrategy(strategy, origin1, origin2);
        exerciseStrategy(strategy, origin1, origin2);
        assertThat(strategy.currentStrategy(), is(busyConnectionsStrategy));

        strategy.originsInventoryStateChanged(newOriginsInventorySnapshot());
        assertThat(strategy.currentStrategy(), is(roundRobinStrategy));
    }

    private LoadBalancingStrategy.Context anyContext() {
        return (LoadBalancingStrategy.Context) anyObject();
    }

    private AdaptiveStrategy exerciseStrategy(AdaptiveStrategy strategy, ConnectionPool... pools) {
        strategy.vote(setOf(pools), UNUSED_CONTEXT);
        return strategy;
    }

    private OriginsInventorySnapshot newOriginsInventorySnapshot() {
        return new OriginsInventorySnapshot(GENERIC_APP, EMPTY_LIST, EMPTY_LIST, EMPTY_LIST);
    }

    private static StubConnectionPool aConnectionPool(Origin origin) {
        return new StubConnectionPool(origin);
    }

    private static Set<ConnectionPool> setOf(ConnectionPool... pools) {
        return newLinkedHashSet(asList(pools));
    }
}