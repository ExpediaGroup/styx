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

import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.connectionpool.SimpleConnectionPool;
import com.hotels.styx.client.connectionpool.stubs.StubConnectionFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observer;

import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;

public class RoundRobinStrategyTest {
    private static final StubConnectionFactory connectionFactory = new StubConnectionFactory();
    private static final ConnectionPool POOL_1 = createConnectionPoolFor("localhost", 1);
    private static final ConnectionPool POOL_2 = createConnectionPoolFor("localhost", 2);
    private static final ConnectionPool POOL_3 = createConnectionPoolFor("localhost", 3);

    private static SimpleConnectionPool createConnectionPoolFor(String host, int port) {
        return new SimpleConnectionPool(
                newOriginBuilder(host, port).build(),
                new ConnectionPoolSettings.Builder().maxConnectionsPerHost(1).build(),
                connectionFactory);
    }

    private LoadBalancingStrategy strategy;

    @BeforeMethod
    public void setUp() {
        //TODO: this is not good for sure, fix it once loadbalancingstrategy interface is corrected
        strategy = new RoundRobinStrategy(newOriginsInventoryBuilder(newBackendServiceBuilder().origins(Origin.newOriginBuilder("localhost", 1).build()).build()).build());
    }

    @Test
    public void returnTheSameOrigins() {
        Iterable<ConnectionPool> sortedOrigins = strategy.vote(asList(POOL_1, POOL_2, POOL_3), null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));
    }

    @Test
    public void skipsExhaustedPools() {
        ConnectionPool first = exhaustPool(createConnectionPoolFor("localhost", 1));
        ConnectionPool second = createConnectionPoolFor("localhost", 2);
        ConnectionPool third = createConnectionPoolFor("localhost", 3);

        Iterable<ConnectionPool> sortedPools = strategy.vote(asList(first, second, third), null);
        assertThat(size(sortedPools), is(2));
    }

    @Test
    public void doesNotReturnPoolWhenAllPoolsAreExhausted() {
        Iterable<ConnectionPool> exhaustedPools = exhaust(
                createConnectionPoolFor("localhost", 1),
                createConnectionPoolFor("localhost", 2),
                createConnectionPoolFor("localhost", 3));

        Iterable<ConnectionPool> sortedPools = strategy.vote(exhaustedPools, null);
        assertThat(size(sortedPools), is(0));
    }

    @Test
    public void cyclesOrigins() {
        Iterable<ConnectionPool> sortedOrigins = strategy.vote(asList(POOL_1, POOL_2, POOL_3), null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));

        sortedOrigins = strategy.vote(asList(POOL_1, POOL_2, POOL_3), null);
        assertThat(sortedOrigins, contains(POOL_2, POOL_3, POOL_1));

        sortedOrigins = strategy.vote(asList(POOL_1, POOL_2, POOL_3), null);
        assertThat(sortedOrigins, contains(POOL_3, POOL_1, POOL_2));

        sortedOrigins = strategy.vote(asList(POOL_1, POOL_2, POOL_3), null);
        assertThat(sortedOrigins, contains(POOL_1, POOL_2, POOL_3));
    }

    private static ConnectionPool exhaustPool(ConnectionPool pool) {
        return exhaust(pool).iterator().next();
    }

    private static Iterable<ConnectionPool> exhaust(ConnectionPool... pools) {
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

    private static ConnectionPool exhaustConnectionPool(ConnectionPool pool) {
        ConnectionCollectingObserver observer = new ConnectionCollectingObserver();
        while (true) {
            pool.borrowConnection().subscribe(observer);
            if (observer.getError() != null) {
                break;
            }
        }
        return pool;
    }
}