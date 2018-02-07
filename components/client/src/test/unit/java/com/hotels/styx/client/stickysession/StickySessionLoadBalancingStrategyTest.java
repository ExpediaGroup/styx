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
package com.hotels.styx.client.stickysession;

import com.google.common.collect.Iterables;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.RemoteHost;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.netty.connectionpool.StubConnectionPool;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.api.client.RemoteHost.remoteHost;
import static com.hotels.styx.client.stickysession.StickySessionCookie.newStickySessionCookie;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class StickySessionLoadBalancingStrategyTest {
    private static final Origin origin0 = newOriginBuilder("localhost", 0).id("o0").build();
    private static final Origin origin1 = newOriginBuilder("localhost", 1).id("o1").build();
    private static final Origin origin2 = newOriginBuilder("localhost", 2).id("o2").build();

    static final RemoteHost ORIGIN_0 = remoteHost(origin0, new StubConnectionPool(origin0), mock(StyxHostHttpClient.class));
    static final RemoteHost ORIGIN_1 = remoteHost(origin1, new StubConnectionPool(origin1), mock(StyxHostHttpClient.class));
    static final RemoteHost ORIGIN_2 = remoteHost(origin2, new StubConnectionPool(origin2), mock(StyxHostHttpClient.class));

    final ActiveOrigins activeOrigins = mock(ActiveOrigins.class);

    final LoadBalancingStrategy FALL_BACK_STRATEGY = (context) -> activeOrigins.snapshot();

    final LoadBalancingStrategy strategy = new StickySessionLoadBalancingStrategy(activeOrigins, FALL_BACK_STRATEGY);


    @Test
    public void selectsThePreferredOrigin() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_0, ORIGIN_1, ORIGIN_2));

        Iterable<RemoteHost> votedOrigins = strategy.vote(context);
        assertThat(first(votedOrigins), is(ORIGIN_1));
    }

    @Test
    public void failsOverToNextAvailableOriginSelectedByTheLoadBalancingStrategyIfRequestedOriginIsNotRegistered() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_0, ORIGIN_2));

        Iterable<RemoteHost> votedOrigins = strategy.vote(context);
        assertThat(first(votedOrigins), is(ORIGIN_0));
    }

    @Test
    public void returnsAnEmptyListIfNoOriginIsAvailable() {
        HttpRequest request = requestWithPreferredOriginSet(ORIGIN_1);
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, request);

        when(activeOrigins.snapshot()).thenReturn(EMPTY_LIST);

        Iterable<RemoteHost> votedOrigins = strategy.vote(context);
        assertThat(votedOrigins, is(emptyIterable()));
    }

    @Test
    public void delegatesToTheChildLoadBalancingStrategyIfNoPreferredOriginIsSet() throws Exception {
        HttpRequest requestWithNoPreferenceOnOrigin = get("/noprefrence").build();
        LoadBalancingStrategy.Context context = new LBContext(GENERIC_APP, requestWithNoPreferenceOnOrigin);

        when(activeOrigins.snapshot()).thenReturn(asList(ORIGIN_2, ORIGIN_1));

        Iterable<RemoteHost> votedOrigins = strategy.vote(context);
        assertThat(first(votedOrigins), is(ORIGIN_2));
    }

    private HttpRequest requestWithPreferredOriginSet(RemoteHost origin) {
        return get("/request")
                .addCookie(stickySessionCookie(origin))
                .build();
    }

    private HttpCookie stickySessionCookie(RemoteHost remoteHost) {
        Origin origin = remoteHost.connectionPool().getOrigin();
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