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

import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.connectionpool.SimpleConnectionPool;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observer;

import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoundRobinStrategyTest {
    private static final StubConnectionFactory connectionFactory = new StubConnectionFactory();
    private static final RemoteHost POOL_1 = createConnectionPoolFor("localhost", 1);
    private static final RemoteHost POOL_2 = createConnectionPoolFor("localhost", 2);
    private static final RemoteHost POOL_3 = createConnectionPoolFor("localhost", 3);
    private static Origin origin;

    private static RemoteHost createConnectionPoolFor(String host, int port) {
        origin = newOriginBuilder(host, port).build();

        return remoteHost(
                origin,
                new SimpleConnectionPool(
                        origin,
                        new ConnectionPoolSettings.Builder().maxConnectionsPerHost(1).build(),
                        connectionFactory),
                mock(StyxHostHttpClient.class));
    }

    private LoadBalancingStrategy strategy;
    private ActiveOrigins activeOriginsMock;

    @BeforeMethod
    public void setUp() {
        activeOriginsMock = mock(ActiveOrigins.class);
        strategy = new RoundRobinStrategy(activeOriginsMock);
    }

    @Test
    public void returnTheSameOrigins() {
        when(activeOriginsMock.snapshot()).thenReturn(asList(POOL_1, POOL_2, POOL_3));
        Iterable<RemoteHost> sortedOrigins = strategy.vote(null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));
    }

    @Test
    public void skipsExhaustedPools() {
        RemoteHost first = exhaustPool(createConnectionPoolFor("localhost", 1));
        RemoteHost second = createConnectionPoolFor("localhost", 2);
        RemoteHost third = createConnectionPoolFor("localhost", 3);

        when(activeOriginsMock.snapshot()).thenReturn(asList(first, second, third));

        Iterable<RemoteHost> sortedPools = strategy.vote(null);
        assertThat(size(sortedPools), is(2));
    }

    @Test
    public void doesNotReturnPoolWhenAllPoolsAreExhausted() {
        Iterable<RemoteHost> exhaustedPools = exhaust(
                createConnectionPoolFor("localhost", 1),
                createConnectionPoolFor("localhost", 2),
                createConnectionPoolFor("localhost", 3));

        when(activeOriginsMock.snapshot()).thenReturn(exhaustedPools);

        Iterable<RemoteHost> sortedPools = strategy.vote(null);
        assertThat(size(sortedPools), is(0));
    }

    @Test
    public void cyclesOrigins() {
        when(activeOriginsMock.snapshot()).thenReturn(asList(POOL_1, POOL_2, POOL_3));
        Iterable<RemoteHost> sortedOrigins = strategy.vote(null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));

        sortedOrigins = strategy.vote(null);
        assertThat(sortedOrigins, contains(POOL_2, POOL_3, POOL_1));

        sortedOrigins = strategy.vote(null);
        assertThat(sortedOrigins, contains(POOL_3, POOL_1, POOL_2));

        sortedOrigins = strategy.vote(null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));
    }

    private static RemoteHost exhaustPool(RemoteHost pool) {
        return exhaust(pool).iterator().next();
    }

    private static Iterable<RemoteHost> exhaust(RemoteHost... pools) {
        return stream(pools)
                .map(RoundRobinStrategyTest::exhaustConnectionPool)
                .collect(toList());
    }

    public static class ConnectionCollectingObserver implements Observer<Connection> {
        private Throwable exception;

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable exception) {
            this.exception = exception;
        }

        @Override
        public void onNext(Connection connection) {
        }

        public Throwable getError() {
            return exception;
        }
    }

    private static RemoteHost exhaustConnectionPool(RemoteHost remoteHost) {
        ConnectionCollectingObserver observer = new ConnectionCollectingObserver();
        while (true) {
            remoteHost.connectionPool().borrowConnection().subscribe(observer);
            if (observer.getError() != null) {
                break;
            }
        }
        return remoteHost;
    }
}