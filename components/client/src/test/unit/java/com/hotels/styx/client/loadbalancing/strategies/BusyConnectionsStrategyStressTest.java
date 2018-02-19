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

import com.google.common.collect.Iterables;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
import static com.hotels.styx.api.support.HostAndPorts.localHostAndFreePort;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.mock;

public class BusyConnectionsStrategyStressTest {
    final Origin ORIGIN_ONE = newOriginBuilder(localHostAndFreePort()).id("one").build();
    final Origin ORIGIN_TWO = newOriginBuilder(localHostAndFreePort()).id("two").build();
    final Origin ORIGIN_THREE = newOriginBuilder(localHostAndFreePort()).id("three").build();
    final Origin ORIGIN_FOUR = newOriginBuilder(localHostAndFreePort()).id("four").build();
    final Origin ORIGIN_FIVE = newOriginBuilder(localHostAndFreePort()).id("five").build();
    final Origin ORIGIN_SIX = newOriginBuilder(localHostAndFreePort()).id("six").build();
    final Origin ORIGIN_SEVEN = newOriginBuilder(localHostAndFreePort()).id("seven").build();
    final Origin ORIGIN_EIGHT = newOriginBuilder(localHostAndFreePort()).id("eight").build();
    final Origin ORIGIN_NINE = newOriginBuilder(localHostAndFreePort()).id("nine").build();

    CodaHaleMetricRegistry metrics;

    @BeforeMethod
    public void setUp() {
        metrics = new CodaHaleMetricRegistry();
    }

    @DataProvider(name = "origins")
    private Object[][] origins() {
        return new Object[][]{
                {new Origin[]{ORIGIN_THREE, ORIGIN_TWO, ORIGIN_ONE, ORIGIN_FOUR}},
                {new Origin[]{ORIGIN_TWO, ORIGIN_THREE, ORIGIN_FOUR, ORIGIN_ONE}},
        };
    }

    @Test
    public void distributesLoadWithoutBiasTowardAnyOrigin() {
        SimulatedOrigin origin1 = new SimulatedOrigin(ORIGIN_ONE);
        SimulatedOrigin origin2 = new SimulatedOrigin(ORIGIN_TWO);
        SimulatedOrigin origin3 = new SimulatedOrigin(ORIGIN_THREE);
        SimulatedOrigin origin4 = new SimulatedOrigin(ORIGIN_FOUR);
        List<SimulatedOrigin> origins = newArrayList(origin1, origin2, origin3, origin4);

        Map<Origin, Integer> results = simulateLoadBalancerTraffic(origins, new SimulatedApp(5.0, origins));

        System.out.println("Requests per origin");
        results.forEach((origin, count) -> System.out.printf("Origin: %-40s count: %7d%n", origin, count));
        System.out.println();

        double maxCount = results.values().stream().max(naturalOrder()).get();
        double minCount = results.values().stream().min(naturalOrder()).get();

        assertThat(maxCount / minCount, is(lessThan(1.1)));
    }

    @Test
    public void distributesLoadWithoutBiasTowardAnyOrigin_X() {
        int responseLatency = 2 * 60 * 1000;

        List<SimulatedOrigin> origins = newArrayList(
                new SimulatedOrigin(ORIGIN_ONE, responseLatency),
                new SimulatedOrigin(ORIGIN_TWO, responseLatency + 200),
                new SimulatedOrigin(ORIGIN_THREE, responseLatency),
                new SimulatedOrigin(ORIGIN_FOUR, responseLatency),
                new SimulatedOrigin(ORIGIN_FIVE, responseLatency + 300),
                new SimulatedOrigin(ORIGIN_SIX, responseLatency),
                new SimulatedOrigin(ORIGIN_SEVEN, responseLatency),
                new SimulatedOrigin(ORIGIN_EIGHT, responseLatency + 400),
                new SimulatedOrigin(ORIGIN_NINE, responseLatency));

        Map<Origin, Integer> results = simulateLoadBalancerTraffic(origins, new SimulatedApp(5.0, origins));

        System.out.println("Requests per origin");
        results.forEach((origin, count) -> System.out.printf("Origin: %-40s count: %7d%n", origin, count));
        System.out.println();

        System.out.println("  existing connections: " + metrics.counter("winner.existingConnections").getCount());
        System.out.println("       new connections: " + metrics.counter("winner.newConnections").getCount());

        double maxCount = results.values().stream().max(naturalOrder()).get();
        double minCount = results.values().stream().min(naturalOrder()).get();

        assertThat(results.size(), is(9));

        assertThat(maxCount / minCount, is(lessThan(1.1)));
    }

    private Map<Origin, Integer> simulateLoadBalancerTraffic(List<SimulatedOrigin> origins, SimulatedApp app) {
        Map<Origin, Integer> results = new TreeMap<>();

        int totalTimeSec = 60 * 60;
        int ticks = totalTimeSec * 1000;
        Random random = new Random();

        for (int i = 0; i < ticks; i++) {

            boolean vote = app.newRequest(1000, random);

            if (vote) {
                Iterable<RemoteHost> pools = origins.stream().map(so ->
                        remoteHost(so.origin, new StubConnectionPool(so.origin())
                                .withBusyConnections(so.busyConnections())
                                .withAvailableConnections(so.availableConnections()), mock(StyxHostHttpClient.class)))
                        .collect(toList());

                final BusyConnectionsStrategy strategy = new BusyConnectionsStrategy(() -> pools);

                Iterable<RemoteHost> result = strategy.vote(contextFromSimulatedOrigins(origins));

                RemoteHost winner = Iterables.get(result, 0);
                if (winner.connectionPool().stats().availableConnectionCount() > 0) {
                    metrics.counter("winner.existingConnections").inc();
                } else {
                    metrics.counter("winner.newConnections").inc();
                }

                results.putIfAbsent(winner.connectionPool().getOrigin(), 0);
                results.computeIfPresent(winner.connectionPool().getOrigin(), (k, v) -> ++v);

                app.borrowFor(i, winner);
            }

            for (SimulatedOrigin so : origins) {
                so.advanceTime(i, 1000, random);
            }
        }

        return results;
    }

    private LoadBalancingStrategy.Context contextFromSimulatedOrigins(List<SimulatedOrigin> origins) {
        return new LoadBalancingStrategy.Context() {
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
                return 0.0;
            }
        };
    }

    private static class SimulatedApp {
        private final List<SimulatedOrigin> simulatedOrigins;
        private final double requestRate;

        private SimulatedApp(double requestRate, List<SimulatedOrigin> simulatedOrigins) {
            this.simulatedOrigins = simulatedOrigins;
            this.requestRate = requestRate;
        }

        public boolean newRequest(int resolution, Random random) {
            return random.nextDouble() < requestRate / (double) resolution;
        }

        public void borrowFor(int currentTime, RemoteHost winner) {
            SimulatedOrigin simulatedOrigin = simulatedOrigins.stream().filter(so -> so.origin().equals(winner.connectionPool().getOrigin())).findFirst().get();
            simulatedOrigin.borrow(currentTime);
        }
    }

    private static class SimulatedOrigin {
        private final int responseLatency;

        private final Origin origin;
        private final List<Integer> ongoingRequests = new ArrayList<>();
        private int maxOngoingrequests = 0;

        public SimulatedOrigin(Origin origin) {
            this(origin, 100);
        }

        public SimulatedOrigin(Origin origin, int responseLatency) {
            this.origin = origin;
            this.responseLatency = responseLatency;
        }

        public void borrow(int currentTime) {
            ongoingRequests.add(currentTime + responseLatency);
            maxOngoingrequests = Integer.max(maxOngoingrequests, ongoingRequests.size());
        }

        public void advanceTime(int currentTime, int resolution, Random random) {
            if (ongoingRequests.size() > 0 && ongoingRequests.get(0) == currentTime) {
                ongoingRequests.remove(0);
            }
        }

        public Origin origin() {
            return origin;
        }

        public int busyConnections() {
            return ongoingRequests.size();
        }

        public int availableConnections() {
            return maxOngoingrequests - ongoingRequests.size();
        }
    }
}
