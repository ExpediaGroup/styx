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
package com.hotels.styx.client.stickysession;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;


public class StickySessionLoadBalancingStrategyTest {
    static final ConnectionPool ORIGIN_0 = new StubConnectionPool(newOriginBuilder("localhost", 0).id("o0").build());
    static final ConnectionPool ORIGIN_1 = new StubConnectionPool(newOriginBuilder("localhost", 1).id("o1").build());
    static final ConnectionPool ORIGIN_2 = new StubConnectionPool(newOriginBuilder("localhost", 2).id("o2").build());

    static final LoadBalancingStrategy FALL_BACK_STRATEGY = (origins, context) -> origins;

    final LoadBalancingStrategy strategy = new StickySessionLoadBalancingStrategy(FALL_BACK_STRATEGY);


    @Test
    public void selectsThePreferredOrigin() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        Iterable<ConnectionPool> votedOrigins = strategy.vote(asList(ORIGIN_0, ORIGIN_1, ORIGIN_2), context);
        assertThat(first(votedOrigins), is(ORIGIN_1));
    }

    @Test
    public void failsOverToNextAvailableOriginSelectedByTheLoadBalancingStrategyIfRequestedOriginIsNotRegistered() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        Iterable<ConnectionPool> votedOrigins = strategy.vote(asList(ORIGIN_0, ORIGIN_2), context);
        assertThat(first(votedOrigins), is(ORIGIN_0));
    }

    @Test
    public void returnsAnEmptyListIfNoOriginIsAvailable() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        Iterable<ConnectionPool> votedOrigins = strategy.vote(EMPTY_LIST, context);
        assertThat(votedOrigins, is(emptyIterable()));
    }

    @Test
    public void delegatesToTheChildLoadBalancingStrategyIfNoPreferredOriginIsSet() throws Exception {
        HttpRequest requestWithNoPreferenceOnOrigin = get("/noprefrence").build();
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, requestWithNoPreferenceOnOrigin);

        Iterable<ConnectionPool> votedOrigins = strategy.vote(asList(ORIGIN_2, ORIGIN_1), context);
        assertThat(first(votedOrigins), is(ORIGIN_2));
    }

    private HttpRequest requestWithPreferredOriginSet(ConnectionPool origin) {
        return get("/request")
                .addCookie(stickySessionCookie(origin))
                .build();
    }

    private HttpCookie stickySessionCookie(ConnectionPool pool) {
        Origin origin = pool.getOrigin();
        return newStickySessionCookie(origin.applicationId(), origin.id(), 86400);
    }

    private static <T> T first(Iterable<T> list) {
        return Iterables.getFirst(list, null);
    }

    private static class LBContext implements LoadBalancingStrategy.Context {
        private final Id appId;
        private final HttpRequest currentRequest;

        private LBContext(Id appId, HttpRequest currentRequest) {
            this.appId = appId;
            this.currentRequest = currentRequest;
        }

        @Override
        public Id appId() {
            return appId;
        }

        @Override
        public HttpRequest currentRequest() {
            return currentRequest;
        }
    }

}