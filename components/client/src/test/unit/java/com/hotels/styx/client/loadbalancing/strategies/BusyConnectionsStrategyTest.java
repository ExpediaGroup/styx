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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import static com.google.common.collect.Iterables.transform;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
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

        RemoteHost poolOne = remoteHost(ORIGIN_ONE, new StubConnectionPool(ORIGIN_ONE, settings).withBusyConnections(4), mock(StyxHostHttpClient.class));
        RemoteHost poolTwo = remoteHost(ORIGIN_TWO, new StubConnectionPool(ORIGIN_TWO, settings).withBusyConnections(3), mock(StyxHostHttpClient.class));
        RemoteHost poolThree = remoteHost(ORIGIN_THREE, new StubConnectionPool(ORIGIN_THREE, settings).withBusyConnections(6), mock(StyxHostHttpClient.class));

        when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo, poolThree));

        Iterable<RemoteHost> sortedPool = strategy.vote(context);
        assertThat(origins(sortedPool), contains(ORIGIN_TWO, ORIGIN_ONE, ORIGIN_THREE));
    }

    @Test
    public void favoursOriginsWithLessLeasedConnectionsCount() {
        ConnectionPool.Settings settings = ConnectionPoolSettings.defaultConnectionPoolSettings();

        RemoteHost poolOne = remoteHost(ORIGIN_ONE, new StubConnectionPool(ORIGIN_ONE, settings)
                .withBusyConnections(2)
                .withPendingConnections(4), mock(StyxHostHttpClient.class));

        RemoteHost poolTwo = remoteHost(ORIGIN_TWO, new StubConnectionPool(ORIGIN_TWO, settings)
                .withBusyConnections(2)
                .withPendingConnections(2), mock(StyxHostHttpClient.class));

        RemoteHost poolThree = remoteHost(ORIGIN_THREE, new StubConnectionPool(ORIGIN_THREE, settings)
                .withBusyConnections(2)
                .withPendingConnections(1), mock(StyxHostHttpClient.class));

        when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo, poolThree));
        Iterable<RemoteHost> sortedPool = strategy.vote(context);
        assertThat(origins(sortedPool), contains(ORIGIN_THREE, ORIGIN_TWO, ORIGIN_ONE));
    }

    @Test
    public void movesOriginsWithNoAvailableConnectionsBehindTheGoodOnes() {
        ConnectionPool.Settings settings = new ConnectionPoolSettings.Builder()
                .maxConnectionsPerHost(10)
                .maxPendingConnectionsPerHost(10)
                .build();

        RemoteHost poolOne = remoteHost(ORIGIN_ONE, new StubConnectionPool(ORIGIN_ONE, settings).withBusyConnections(1).withAvailableConnections(0), mock(StyxHostHttpClient.class));
        RemoteHost poolTwo = remoteHost(ORIGIN_TWO, new StubConnectionPool(ORIGIN_TWO, settings).withBusyConnections(2).withAvailableConnections(1), mock(StyxHostHttpClient.class));
        RemoteHost poolThree = remoteHost(ORIGIN_THREE, new StubConnectionPool(ORIGIN_THREE, settings).withBusyConnections(3).withAvailableConnections(1), mock(StyxHostHttpClient.class));

        when(activeOrigins.snapshot()).thenReturn(asList(poolTwo, poolOne, poolThree));
        Iterable<RemoteHost> sortedPool = strategy.vote(context);

        assertThat(origins(sortedPool), contains(ORIGIN_ONE, ORIGIN_TWO, ORIGIN_THREE));
    }

    @Test
    public void ranksOriginsWithPoolDepthFirstThenOnAvailableConnections() {
        RemoteHost poolOne = remoteHost(ORIGIN_ONE, new StubConnectionPool(ORIGIN_ONE).withBusyConnections(1).withAvailableConnections(0), mock(StyxHostHttpClient.class));
        RemoteHost poolTwo = remoteHost(ORIGIN_TWO, new StubConnectionPool(ORIGIN_TWO).withBusyConnections(1).withAvailableConnections(1), mock(StyxHostHttpClient.class));
        RemoteHost poolThree = remoteHost(ORIGIN_THREE, new StubConnectionPool(ORIGIN_THREE).withBusyConnections(2).withAvailableConnections(1), mock(StyxHostHttpClient.class));

        when(activeOrigins.snapshot()).thenReturn(asList(poolTwo, poolOne, poolThree));
        Iterable<RemoteHost> sortedPool = strategy.vote(mock(LoadBalancingStrategy.Context.class));

        assertThat(origins(sortedPool), contains(ORIGIN_TWO, ORIGIN_ONE, ORIGIN_THREE));
    }

    @Test
    public void negativeBusyConnectionCount() {
        RemoteHost poolOne = remoteHost(ORIGIN_ONE, new StubConnectionPool(ORIGIN_ONE).withBusyConnections(1), mock(StyxHostHttpClient.class));
        RemoteHost poolTwo = remoteHost(ORIGIN_TWO, new StubConnectionPool(ORIGIN_TWO).withBusyConnections(0), mock(StyxHostHttpClient.class));

        Iterable<RemoteHost> sortedPool = null;
        for (int i = 0; i < 10; i++) {
            when(activeOrigins.snapshot()).thenReturn(asList(poolOne, poolTwo));
            sortedPool = strategy.vote(context);
        }

        assertThat(origins(sortedPool), contains(ORIGIN_TWO, ORIGIN_ONE));
    }

    private static Iterable<Origin> origins(Iterable<RemoteHost> remoteHosts) {
        return transform(remoteHosts, remoteHost -> remoteHost.connectionPool().getOrigin());
    }
}