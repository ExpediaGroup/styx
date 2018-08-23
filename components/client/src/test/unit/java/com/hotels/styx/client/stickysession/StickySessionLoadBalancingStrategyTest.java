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
package com.hotels.styx.client.stickysession;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Iterables.getFirst;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class StickySessionLoadBalancingStrategyTest {
    private static final Origin origin0 = newOriginBuilder("localhost", 0).id("o0").build();
    private static final Origin origin1 = newOriginBuilder("localhost", 1).id("o1").build();
    private static final Origin origin2 = newOriginBuilder("localhost", 2).id("o2").build();

    static final RemoteHost ORIGIN_0 = remoteHost(origin0, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class));
    static final RemoteHost ORIGIN_1 = remoteHost(origin1, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class));
    static final RemoteHost ORIGIN_2 = remoteHost(origin2, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class));

    final ActiveOrigins activeOrigins = mock(ActiveOrigins.class);

    final LoadBalancer FALL_BACK_STRATEGY = (context) -> Optional.ofNullable(getFirst(activeOrigins.snapshot(), null));

    final LoadBalancer strategy = new StickySessionLoadBalancingStrategy(activeOrigins, FALL_BACK_STRATEGY);


    @Test
    public void selectsThePreferredOrigin() {
        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_0, ORIGIN_1, ORIGIN_2));

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreferences(Optional.of(ORIGIN_1.id())));

        assertThat(chosenOne, is(Optional.of(ORIGIN_1)));
    }

    @Test
    public void failsOverToNextAvailableOriginSelectedByTheLoadBalancingStrategyIfRequestedOriginIsNotRegistered() {
        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_0, ORIGIN_2));

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreferences(Optional.of(ORIGIN_1.id())));

        assertThat(chosenOne, is(Optional.of(ORIGIN_0)));
    }

    @Test
    public void returnsAnEmptyOptionWhenNoOriginsAreAvailable() {
        when(activeOrigins.snapshot()).thenReturn(EMPTY_LIST);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreferences(Optional.of(ORIGIN_1.id())));

        assertThat(chosenOne, is(Optional.empty()));

    }

    @Test
    public void delegatesToTheChildLoadBalancingStrategyIfNoPreferredOriginIsSet() throws Exception {
        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_2, ORIGIN_1));

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreferences(Optional.empty()));

        assertThat(chosenOne, is(Optional.of(ORIGIN_2)));
    }


    private LoadBalancer.Preferences lbPreferences(Optional<Id> id) {
        return new LoadBalancer.Preferences() {
            @Override
            public Optional<String> preferredOrigins() {
                return id.map(Id::toString);
            }

            @Override
            public List<Origin> avoidOrigins() {
                return null;
            }
        };
    }

}