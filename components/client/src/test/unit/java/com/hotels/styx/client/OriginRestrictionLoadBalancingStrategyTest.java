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
package com.hotels.styx.client;

import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingMetricSupplier;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.mockito.Mockito.mock;

public class OriginRestrictionLoadBalancingStrategyTest {
    List<RemoteHost> origins = Stream.of(0, 1, 2, 3, 4, 5, 6)
            .map(i -> newOriginBuilder("localhost", 8080 + i).id("origin-" + i).build())
            .map(origin -> remoteHost(origin, mock(StyxHostHttpClient.class), mock(LoadBalancingMetricSupplier.class)))
            .collect(toList());

    private final LoadBalancer delegate = (preferences) -> Optional.of(origins.get(0));

    OriginRestrictionLoadBalancingStrategy strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate);

    @Test
    public void randomlyChoosesOneOfTheMatchingOrigins() {
        Map<Id, Integer> frequencies = new HashMap<>();
        frequencies.putIfAbsent(origins.get(0).id(), 0);
        frequencies.putIfAbsent(origins.get(1).id(), 0);
        frequencies.putIfAbsent(origins.get(2).id(), 0);
        frequencies.putIfAbsent(origins.get(3).id(), 0);
        frequencies.putIfAbsent(origins.get(4).id(), 0);
        frequencies.putIfAbsent(origins.get(5).id(), 0);
        frequencies.putIfAbsent(origins.get(6).id(), 0);

        for (int i = 0; i < 100; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-[1-3], origin-(5|6)")));
            frequencies.compute(chosenOne.get().id(), (id, value) -> value + 1);
        }

        assertThat(frequencies.get(origins.get(0).id()), is(0));
        assertThat(frequencies.get(origins.get(4).id()), is(0));

        assertThat(frequencies.get(origins.get(1).id()), greaterThan(0));
        assertThat(frequencies.get(origins.get(2).id()), greaterThan(0));
        assertThat(frequencies.get(origins.get(3).id()), greaterThan(0));
        assertThat(frequencies.get(origins.get(5).id()), greaterThan(0));
        assertThat(frequencies.get(origins.get(6).id()), greaterThan(0));
    }

    @Test
    public void shouldDisregardRestrictionCookieValueIfNotValid() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("*-01")));
            assertThat(chosenOne.get(), isOneOf(origins.toArray()));
        }
    }

    @Test
    public void usesSingleOriginMatchingRegularExpression() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-1")));
            assertThat(chosenOne.get(), is(origins.get(1)));
        }
    }

    @Test
    public void usesMultipleOriginsMatchingRegularExpression() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-[2-4]")));
            assertThat(chosenOne.get(), isOneOf(origins.get(2), origins.get(3), origins.get(4)));
        }
    }

    @Test
    public void usesNoOriginsWhenRegularExpressionMatchesNone() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("foo")));
            assertThat(chosenOne, is(Optional.empty()));
        }
    }

    @Test
    public void usesAllOriginsWhenCookieIsAbsent() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.empty()));
            assertThat(chosenOne.get(), is(origins.get(0)));
        }
    }

    @Test
    public void usesAllOriginsWhenRegularExpressionMatchesAll() {
        for (int i = 0; i < 10; i++) {
            Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of(".*")));
            assertThat(chosenOne.get(), isOneOf(origins.toArray()));
        }
    }

    // TODO: MIKKO: Add test for the error log message about invalid patterns.
    // Ensure that full stack trace is not printed.

    private static LoadBalancer.Preferences lbPreference(Optional<String> preferredOrigins) {
        return new LoadBalancer.Preferences() {

            @Override
            public Optional<String> preferredOrigins() {
                return preferredOrigins;
            }

            @Override
            public List<Origin> avoidOrigins() {
                return null;
            }
        };
    }
}