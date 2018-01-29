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

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.OriginsInventory.RemoteHostWrapper;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;

public class OriginRestrictionLoadBalancingStrategyTest {
    List<RemoteHost> origins = Stream.of(0, 1, 2, 3, 4, 5, 6)
            .map(this::origin)
            .collect(toList());

    OriginRestrictionLoadBalancingStrategy strategy = new OriginRestrictionLoadBalancingStrategy(
            () -> origins, (context) -> origins, "originRestrictionCookie");

    @Test
    public void shouldDisregardRestrictionCookieValueIfNotValid() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", "*-01")));

        assertThat(partition, contains(origins.toArray()));
    }

    @Test
    public void usesSingleOriginMatchingRegularExpression() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", "origin1")));

        assertThat(partition, contains(origins.get(1)));
    }

    @Test
    public void usesMultipleOriginsMatchingRegularExpression() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", "origin[2-4]")));

        assertThat(partition, contains(origins.get(2), origins.get(3), origins.get(4)));
    }

    @Test
    public void usesNoOriginsWhenRegularExpressionMatchesNone() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", "foo")));

        assertThat(partition, is(emptyIterable()));
    }

    @Test
    public void usesAllOriginsWhenCookieIsAbsent() {
        Iterable<RemoteHost> partition = strategy.vote(context());

        assertThat(partition, contains(origins.toArray()));
    }

    @Test
    public void usesAllOriginsWhenRegularExpressionMatchesAll() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", ".*")));

        assertThat(partition, contains(origins.toArray()));
    }

    @Test
    public void usesOriginsMatchingAnyOfAListOfRegularExpressions() {
        Iterable<RemoteHost> partition = strategy.vote(contextWith(request ->
                request.addCookie("originRestrictionCookie", "origin[1-3], origin(5|6)")));

        assertThat(partition, contains(origins.get(1), origins.get(2), origins.get(3), origins.get(5), origins.get(6)));
    }

    private RemoteHost origin(int number) {
        return new RemoteHostWrapper(new StubConnectionPool(
                newOriginBuilder("localhost", 8080 + number)
                        .id("origin" + number)
                        .build()));
    }

    static StubContext context() {
        return new StubContext(identity());
    }

    static StubContext contextWith(Function<HttpRequest.Builder, HttpRequest.Builder> requestModification) {
        return new StubContext(requestModification);
    }

    static class StubContext implements LoadBalancingStrategy.Context {
        Function<HttpRequest.Builder, HttpRequest.Builder> requestModification;

        public StubContext(Function<HttpRequest.Builder, HttpRequest.Builder> requestModification) {
            this.requestModification = requestModification;
        }

        @Override
        public Id appId() {
            return GENERIC_APP;
        }

        @Override
        public HttpRequest currentRequest() {
            return requestModification.apply(get("/"))
                    .build();
        }
    }
}