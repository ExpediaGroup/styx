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
package com.hotels.styx.client;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OriginRestrictionLoadBalancingStrategyTest {
    List<RemoteHost> origins = Stream.of(0, 1, 2, 3, 4, 5, 6)
            .map(i -> newOriginBuilder("localhost", 8080 + i).id("origin-" + i).build())
            .map(origin -> remoteHost(origin, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)))
            .collect(toList());

    private LoadBalancer delegate;

    private OriginRestrictionLoadBalancingStrategy strategy;
    private LoggingTestSupport log;

    @BeforeMethod
    public void setUp() {
        delegate = mock(LoadBalancer.class);
        when(delegate.choose(any(LoadBalancer.Preferences.class))).thenReturn(Optional.of(origins.get(0)));

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate);

        log = new LoggingTestSupport(OriginRestrictionLoadBalancingStrategy.class);
    }

    @AfterMethod
    public void tearDown() {
        log.stop();
    }

    @Test
    public void randomlyChoosesOneOfTheMatchingOrigins() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextInt(5)).thenReturn(3);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-[1-3], origin-(5|6)")));

        // The preferred origins are [1, 2, 3, 5, 6]
        // The index 3 (as chosen by rng) contains:
        assertThat(chosenOne.get().id(), is(id("origin-5")));
    }

    @Test
    public void randomlyChoosesOneOfTheOriginsWithInvalidPreferences() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextInt(any(Integer.class))).thenReturn(3);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("*-01")));

        // Chooses one from *all (= 7)* origins:
        verify(mockRandom).nextInt(eq(7));

        assertThat(chosenOne.get().id(), is(id("origin-3")));
    }

    @Test
    public void usesSingleOriginMatchingRegularExpression() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextInt(eq(1))).thenReturn(0);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-1")));
        assertThat(chosenOne.get(), is(origins.get(1)));
    }

    @Test
    public void usesMultipleOriginsMatchingRegularExpression() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextInt(eq(3))).thenReturn(1);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("origin-[2-4]")));
        verify(mockRandom).nextInt(eq(3));
        assertThat(chosenOne.get(), is(origins.get(3)));
    }

    @Test
    public void usesNoOriginsWhenRegularExpressionMatchesNone() {
        Random mockRandom = mock(Random.class);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of("foo")));
        assertThat(chosenOne, is(empty()));
        verify(mockRandom, never()).nextInt(any(Integer.class));
    }

    @Test
    public void usesAllOriginsWhenCookieIsAbsent() {
        Random mockRandom = mock(Random.class);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(empty()));
        assertThat(chosenOne.get(), is(origins.get(0)));

        verify(mockRandom, never()).nextInt(any(Integer.class));
        verify(delegate).choose(any(LoadBalancer.Preferences.class));
    }

    @Test
    public void usesAllOriginsWhenRegularExpressionMatchesAll() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextInt(any(Integer.class))).thenReturn(3);

        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        Optional<RemoteHost> chosenOne = strategy.choose(lbPreference(Optional.of(".*")));
        assertThat(chosenOne.get(), isOneOf(origins.get(3)));
        verify(mockRandom).nextInt(eq(7));
    }


    @Test
    public void logsInvalidPatterns() {
        Random mockRandom = mock(Random.class);
        strategy = new OriginRestrictionLoadBalancingStrategy(() -> origins, delegate, mockRandom);

        strategy.choose(lbPreference(Optional.of("*-01")));

        assertThat(log.lastMessage(), is(loggingEvent(ERROR, "Invalid origin restriction cookie value=.*, Cause=Dangling meta character .*")));
    }

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