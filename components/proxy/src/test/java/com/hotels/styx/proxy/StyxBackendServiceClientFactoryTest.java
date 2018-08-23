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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetric;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.StyxHostHttpClient;
import com.hotels.styx.client.StyxHttpClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.service.BackendService.newBackendServiceBuilder;
import static com.hotels.styx.api.extension.service.StickySessionConfig.newStickySessionConfigBuilder;
import static com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder;
import static com.hotels.styx.client.connectionpool.ConnectionPools.simplePoolFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static rx.Observable.just;

public class StyxBackendServiceClientFactoryTest {
    private Environment environment;
    private Connection.Factory connectionFactory;
    private BackendService backendService;
    private String STICKY_COOKIE = "styx_origin_" + GENERIC_APP;
    private String ORIGINS_RESTRICTION_COOKIE = "styx-origins-restriction";

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(Connection.Factory.class);
        environment = new Environment.Builder().build();
        backendService = newBackendServiceBuilder()
                .origins(newOriginBuilder("localhost", 8081).build())
                .build();

        when(connectionFactory.createConnection(any(Origin.class), any(ConnectionSettings.class)))
                .thenReturn(just(mock(Connection.class)));
    }

    @Test
    public void createsClients() {
        StyxBackendServiceClientFactory factory = new StyxBackendServiceClientFactory(environment);

        OriginsInventory originsInventory = newOriginsInventoryBuilder(backendService.id())
                .connectionPoolFactory(simplePoolFactory())
                .initialOrigins(backendService.origins())
                .build();

        OriginStatsFactory originStatsFactory = mock(OriginStatsFactory.class);

        HttpClient client = factory.createClient(backendService, originsInventory, originStatsFactory);

        assertThat(client, is(instanceOf(StyxHttpClient.class)));

        // note: for more meaningful tests, perhaps we need to add package-private accessor methods to StyxHttpClient
        //       there is also the issue that the Transport class assumes it will get a NettyConnection, so mocks can't be used
        //       these problems are both outside the scope of the current issue being implemented
        //       so for the time being, this test is mostly a placeholder
    }


    @Test
    public void usesTheOriginSpecifiedInTheStickySessionCookie() {
        BackendService backendService = newBackendServiceBuilder()
                .origins(
                        newOriginBuilder("localhost", 9091).id("x").build(),
                        newOriginBuilder("localhost", 9092).id("y").build(),
                        newOriginBuilder("localhost", 9093).id("z").build())
                .stickySessionConfig(
                        newStickySessionConfigBuilder()
                                .enabled(true)
                                .build())
                .build();

        HttpClient styxHttpClient = new StyxBackendServiceClientFactory(environment)
                .createClient(
                        backendService,
                        newOriginsInventoryBuilder(backendService)
                                .hostClientFactory((pool) -> {
                                    if (pool.getOrigin().id().equals(id("x"))) {
                                        return hostClient(response(OK).header("X-Origin-Id", "x").build());
                                    } else if (pool.getOrigin().id().equals(id("y"))) {
                                        return hostClient(response(OK).header("X-Origin-Id", "y").build());
                                    } else {
                                        return hostClient(response(OK).header("X-Origin-Id", "z").build());
                                    }
                                })
                                .build(),
                        new OriginStatsFactory(new CodaHaleMetricRegistry()));

        HttpRequest requestz = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("z").toString())).build();
        HttpRequest requestx = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("x").toString())).build();
        HttpRequest requesty = get("/some-req").cookies(requestCookie(STICKY_COOKIE, id("y").toString())).build();

        HttpResponse responsez = styxHttpClient.sendRequest(requestz).toBlocking().first();
        HttpResponse responsex = styxHttpClient.sendRequest(requestx).toBlocking().first();
        HttpResponse responsey = styxHttpClient.sendRequest(requesty).toBlocking().first();

        assertThat(responsex.header("X-Origin-Id").get(), is("x"));
        assertThat(responsey.header("X-Origin-Id").get(), is("y"));
        assertThat(responsez.header("X-Origin-Id").get(), is("z"));
    }

    @Test
    public void usesTheOriginSpecifiedInTheOriginsRestrictionCookie() {
        MapBackedConfiguration config = new MapBackedConfiguration();
        config.set("originRestrictionCookie", ORIGINS_RESTRICTION_COOKIE);

        environment = new Environment.Builder()
                .configuration(new StyxConfig(config))
                .build();

        BackendService backendService = newBackendServiceBuilder()
                .origins(
                        newOriginBuilder("localhost", 9091).id("x").build(),
                        newOriginBuilder("localhost", 9092).id("y").build(),
                        newOriginBuilder("localhost", 9093).id("z").build())
                .build();

        HttpClient styxHttpClient = new StyxBackendServiceClientFactory(environment)
                .createClient(
                        backendService,
                        newOriginsInventoryBuilder(backendService)
                                .hostClientFactory((pool) -> {
                                    if (pool.getOrigin().id().equals(id("x"))) {
                                        return hostClient(response(OK).header("X-Origin-Id", "x").build());
                                    } else if (pool.getOrigin().id().equals(id("y"))) {
                                        return hostClient(response(OK).header("X-Origin-Id", "y").build());
                                    } else {
                                        return hostClient(response(OK).header("X-Origin-Id", "z").build());
                                    }
                                })
                                .build(),
                        new OriginStatsFactory(new CodaHaleMetricRegistry()));

        HttpRequest requestz = get("/some-req").cookies(requestCookie(ORIGINS_RESTRICTION_COOKIE, id("z").toString())).build();
        HttpRequest requestx = get("/some-req").cookies(requestCookie(ORIGINS_RESTRICTION_COOKIE, id("x").toString())).build();
        HttpRequest requesty = get("/some-req").cookies(requestCookie(ORIGINS_RESTRICTION_COOKIE, id("y").toString())).build();

        HttpResponse responsez = styxHttpClient.sendRequest(requestz).toBlocking().first();
        HttpResponse responsex = styxHttpClient.sendRequest(requestx).toBlocking().first();
        HttpResponse responsey = styxHttpClient.sendRequest(requesty).toBlocking().first();

        assertThat(responsex.header("X-Origin-Id").get(), is("x"));
        assertThat(responsey.header("X-Origin-Id").get(), is("y"));
        assertThat(responsez.header("X-Origin-Id").get(), is("z"));
    }

    private StyxHostHttpClient hostClient(HttpResponse response) {
        StyxHostHttpClient mockClient = mock(StyxHostHttpClient.class);
        when(mockClient.sendRequest(any(HttpRequest.class))).thenReturn(just(response));
        when(mockClient.loadBalancingMetric()).thenReturn(new LoadBalancingMetric(1));
        return mockClient;
    }

}