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
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.common.HostAndPorts.localHostAndFreePort;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BusyConnectionsStrategyTest {
    private static final Origin ORIGIN_ONE = newOriginBuilder(localHostAndFreePort()).id("one").build();
    private static final Origin ORIGIN_TWO = newOriginBuilder(localHostAndFreePort()).id("two").build();
    private static final Origin ORIGIN_THREE = newOriginBuilder(localHostAndFreePort()).id("three").build();

    private final ActiveOrigins activeOrigins = mock(ActiveOrigins.class);
    private final BusyConnectionsStrategy strategy = new BusyConnectionsStrategy(activeOrigins);


    @Test
    public void tieBreaksOriginsWithEqualMetric() {
        RemoteHost hostOne = remoteHost(ORIGIN_ONE, mock(HttpHandler.class), lbMetrics(3));
        RemoteHost hostTwo = remoteHost(ORIGIN_TWO, mock(HttpHandler.class), lbMetrics(3));
        RemoteHost hostThree = remoteHost(ORIGIN_THREE, mock(HttpHandler.class), lbMetrics(3));

        when(activeOrigins.snapshot()).thenReturn(asList(hostOne, hostTwo, hostThree));

        List<RemoteHost> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(strategy.choose(null).get());
        }

        assertThat(count(hostOne, results), greaterThan(10L));
        assertThat(count(hostTwo, results), greaterThan(10L));
        assertThat(count(hostThree, results), greaterThan(10L));
    }

    @Test
    public void favoursOriginsWithLessBusyConnectionCount() {
        RemoteHost hostOne = remoteHost(ORIGIN_ONE, mock(HttpHandler.class), lbMetrics(4));
        RemoteHost hostTwo = remoteHost(ORIGIN_TWO, mock(HttpHandler.class), lbMetrics(3));
        RemoteHost hostThree = remoteHost(ORIGIN_THREE, mock(HttpHandler.class), lbMetrics(6));

        when(activeOrigins.snapshot()).thenReturn(asList(hostOne, hostTwo, hostThree));

        Optional<RemoteHost> sortedPool = strategy.choose(null);
        assertThat(sortedPool, is(Optional.of(hostTwo)));
    }

    @Test
    public void copesWithOnlyOneActiveOrigin() {
        RemoteHost hostOne = remoteHost(ORIGIN_ONE, mock(HttpHandler.class), lbMetrics(4));

        when(activeOrigins.snapshot()).thenReturn(asList(hostOne));

        Optional<RemoteHost> sortedPool = strategy.choose(null);
        assertThat(sortedPool, is(Optional.of(hostOne)));
    }

    @Test
    public void returnsEmptyOptionalWhenNoActiveOrigins() {
        when(activeOrigins.snapshot()).thenReturn(asList());

        Optional<RemoteHost> sortedPool = strategy.choose(null);
        assertThat(sortedPool, is(Optional.empty()));
    }

    private long count(RemoteHost host, List<RemoteHost> results) {
        long count = results.stream().filter(h -> h == host).count();
        return count;
    }

    private LoadBalancingMetricSupplier lbMetrics(int ongoing) {
        return () -> new LoadBalancingMetric(ongoing);
    }

}
