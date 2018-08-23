/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.client.loadbalancing.strategies;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.common.HostAndPorts.localHostAndFreePort;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PowerOfTwoStrategyTest {
    final Origin ORIGIN_ONE = newOriginBuilder(localHostAndFreePort()).id("one").build();
    final Origin ORIGIN_TWO = newOriginBuilder(localHostAndFreePort()).id("two").build();
    final Origin ORIGIN_THREE = newOriginBuilder(localHostAndFreePort()).id("three").build();
    final Origin ORIGIN_FOUR = newOriginBuilder(localHostAndFreePort()).id("four").build();

    private final RemoteHost HOST_ONE = remoteHost(ORIGIN_ONE, mock(HttpHandler.class), lbMetrics(5));
    private final RemoteHost HOST_TWO = remoteHost(ORIGIN_TWO, mock(HttpHandler.class), lbMetrics(6));
    private final RemoteHost HOST_THREE = remoteHost(ORIGIN_THREE, mock(HttpHandler.class), lbMetrics(3));
    private final RemoteHost HOST_FOUR = remoteHost(ORIGIN_FOUR, mock(HttpHandler.class), lbMetrics(2));
    List<RemoteHost> allOrigins = asList(HOST_ONE, HOST_TWO, HOST_THREE,  HOST_FOUR);

    private long RNG_SEED = 5;

    @Test
    public void choosesBetterOfTwoRandomChoices() {
        ActiveOrigins activeOrigins = mock(ActiveOrigins.class);
        when(activeOrigins.snapshot()).thenReturn(allOrigins);

        Random rng = new Random(RNG_SEED);
        int first = rng.nextInt(4);
        assert(first == 2);

        int second = rng.nextInt(4);
        assert(second == 0);

        PowerOfTwoStrategy loadBalancer = new PowerOfTwoStrategy(activeOrigins, new Random(RNG_SEED));
        Optional<RemoteHost> chosenOne = loadBalancer.choose(mock(LoadBalancer.Preferences.class));

        assertThat(chosenOne.get(), is(betterOf(allOrigins.get(first), allOrigins.get(second))));
    }

    @Test
    public void choosesSoleOriginOutOfOne() {
        ActiveOrigins activeOrigins = mock(ActiveOrigins.class);
        when(activeOrigins.snapshot()).thenReturn(asList(HOST_ONE));

        PowerOfTwoStrategy loadBalancer = new PowerOfTwoStrategy(activeOrigins, new Random(RNG_SEED));

        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = loadBalancer.choose(mock(LoadBalancer.Preferences.class));
            assertThat(chosenOne.get(), is(HOST_ONE));
        }
    }

    @Test
    public void returnsEmptyWhenNoOriginsAreAvailable() {
        ActiveOrigins activeOrigins = mock(ActiveOrigins.class);
        when(activeOrigins.snapshot()).thenReturn(asList());

        PowerOfTwoStrategy loadBalancer = new PowerOfTwoStrategy(activeOrigins, new Random(RNG_SEED));
        Optional<RemoteHost> chosenOne = loadBalancer.choose(mock(LoadBalancer.Preferences.class));

        assertThat(chosenOne, is(Optional.empty()));

    }

    private RemoteHost betterOf(RemoteHost first, RemoteHost second) {
        return first.metric().ongoingConnections() < second.metric().ongoingConnections() ? first : second;
    }

    private LoadBalancingMetricSupplier lbMetrics(int ongoing) {
        return () -> new LoadBalancingMetric(ongoing);
    }

}