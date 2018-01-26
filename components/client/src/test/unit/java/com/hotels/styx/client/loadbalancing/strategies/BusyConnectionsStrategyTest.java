/**
w * Copyright (C) 2013-2018 Expedia Inc.
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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import java.util.Map;

import static com.google.common.collect.Iterables.transform;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.support.HostAndPorts.localHostAndFreePort;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BusyConnectionsStrategyTest {
    final Origin ORIGIN_ONE = newOriginBuilder(localHostAndFreePort()).id("one").build();
    final Origin ORIGIN_TWO = newOriginBuilder(localHostAndFreePort()).id("two").build();
    final Origin ORIGIN_THREE = newOriginBuilder(localHostAndFreePort()).id("three").build();
    final Origin ORIGIN_FOUR = newOriginBuilder(localHostAndFreePort()).id("four").build();

    ActiveOrigins activeOrigins = mock(ActiveOrigins.class);
    final BusyConnectionsStrategy strategy = new BusyConnectionsStrategy(activeOrigins);

    private LoadBalancingStrategy.Context context = new LoadBalancingStrategy.Context() {
        @Override
        public Id appId() {
            return null;
        }

        @Override
        public HttpRequest currentRequest() {
            return null;
        }

        @Override
        public double oneMinuteRateForStatusCode5xx(Origin origin) {
            return 1.0;
        }
    };

    @Test
    public void favoursOriginsWithLessBusyConnectionCount() {
        ConnectionPool.Settings settings = ConnectionPoolSettings.defaultConnectionPoolSettings();

        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE, settings).withBusyConnections(4);
        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO, settings).withBusyConnections(3);
        ConnectionPool poolThree = new StubConnectionPool(ORIGIN_THREE, settings).withBusyConnections(6);

        when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo, poolThree));

        Iterable<ConnectionPool> sortedPool = strategy.vote(context);
        assertThat(origins(sortedPool), contains(ORIGIN_TWO, ORIGIN_ONE, ORIGIN_THREE));
    }

    @Test
    public void favoursOriginsWithLessLeasedConnectionsCount() {
        ConnectionPool.Settings settings = ConnectionPoolSettings.defaultConnectionPoolSettings();

        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE, settings)
                .withBusyConnections(2)
                .withPendingConnections(4);

        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO, settings)
                .withBusyConnections(2)
                .withPendingConnections(2);

        ConnectionPool poolThree = new StubConnectionPool(ORIGIN_THREE, settings)
                .withBusyConnections(2)
                .withPendingConnections(1);

        when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo, poolThree));
        Iterable<ConnectionPool> sortedPool = strategy.vote(context);
        assertThat(origins(sortedPool), contains(ORIGIN_THREE, ORIGIN_TWO, ORIGIN_ONE));
    }


    @Test
    public void movesHigherThanAverage500RatesToBackOfTheList() {
        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE).withBusyConnections(2);
        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO).withBusyConnections(1);
        ConnectionPool poolThree = new StubConnectionPool(ORIGIN_THREE).withBusyConnections(3);

        when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo, poolThree));
        Iterable<ConnectionPool> sortedPool = strategy.vote(lbContextWith5xxRates(ImmutableMap.of(
                ORIGIN_ONE, 2.0,
                ORIGIN_TWO, 3.0,
                ORIGIN_THREE, 1.0)));

        assertThat(origins(sortedPool), contains(ORIGIN_ONE, ORIGIN_THREE, ORIGIN_TWO));
    }

    @Test
    public void movesOriginsWithNoAvailableConnectionsBehindTheGoodOnes() {
        ConnectionPool.Settings settings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(10)
                .maxPendingConnectionsPerHost(10)
                .build();

        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE, settings).withBusyConnections(1).withAvailableConnections(0);
        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO, settings).withBusyConnections(2).withAvailableConnections(1);
        ConnectionPool poolThree = new StubConnectionPool(ORIGIN_THREE, settings).withBusyConnections(3).withAvailableConnections(1);

        when(activeOrigins.snapshot()).thenReturn(asList(poolTwo, poolOne, poolThree));
        Iterable<ConnectionPool> sortedPool = strategy.vote(context);

        assertThat(origins(sortedPool), contains(ORIGIN_ONE, ORIGIN_TWO, ORIGIN_THREE));
    }

    @Test
    public void ranksOriginsWith500ErrorRateFirstThenPoolDepthThenOnAvailableConnections() {
        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE).withBusyConnections(1).withAvailableConnections(0);
        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO).withBusyConnections(1).withAvailableConnections(1);
        ConnectionPool poolThree = new StubConnectionPool(ORIGIN_THREE).withBusyConnections(1).withAvailableConnections(1);
        ConnectionPool poolFour = new StubConnectionPool(ORIGIN_FOUR).withBusyConnections(2).withAvailableConnections(1);

        when(activeOrigins.snapshot()).thenReturn(asList(poolTwo, poolOne, poolThree, poolFour));
        Iterable<ConnectionPool> sortedPool = strategy.vote(lbContextWith5xxRates(ImmutableMap.of(
                ORIGIN_ONE, 1.0,
                ORIGIN_TWO, 3.0,
                ORIGIN_THREE, 1.0,
                ORIGIN_FOUR, 1.0
        )));

        assertThat(origins(sortedPool), contains(ORIGIN_THREE, ORIGIN_ONE, ORIGIN_FOUR, ORIGIN_TWO));
    }

    @Test
    public void negativeBusyConnectionCount() {
        ConnectionPool poolOne = new StubConnectionPool(ORIGIN_ONE).withBusyConnections(1);
        ConnectionPool poolTwo = new StubConnectionPool(ORIGIN_TWO).withBusyConnections(0);

        Iterable<ConnectionPool> sortedPool = null;
        for (int i = 0; i < 10; i++) {
            when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo));
            sortedPool = strategy.vote(context);
        }

        assertThat(origins(sortedPool), contains(ORIGIN_TWO, ORIGIN_ONE));
    }

    private LBContextAdapter lbContextWith5xxRates(Map<Origin, Double> ratesForOrigins) {
        return new LBContextAdapter() {
            @Override
            public double oneMinuteRateForStatusCode5xx(Origin origin) {
                return ratesForOrigins.get(origin);
            }
        };
    }

    private static Iterable<Origin> origins(Iterable<ConnectionPool> pools) {
        return transform(pools, ConnectionPool::getOrigin);
    }


    static class LBContextAdapter implements LoadBalancingStrategy.Context {
        @Override
        public Id appId() {
            return null;
        }

        @Override
        public HttpRequest currentRequest() {
            return null;
        }
    }
}