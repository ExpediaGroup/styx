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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpClient;
import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.client.applications.BackendService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.OriginsInventory.newOriginsInventoryBuilder;
import static com.hotels.styx.client.applications.BackendService.newBackendServiceBuilder;
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

    @BeforeMethod
    public void setUp() {
        connectionFactory = mock(Connection.Factory.class);
        environment = new Environment.Builder().build();
        backendService = newBackendServiceBuilder()
                .origins(newOriginBuilder("localhost", 8081).build())
                .build();

        when(connectionFactory.createConnection(any(Origin.class), any(Connection.Settings.class)))
                .thenReturn(just(mock(Connection.class)));
    }

    @Test
    public void createsClients() {
        StyxBackendServiceClientFactory factory = new StyxBackendServiceClientFactory(environment);

        OriginsInventory originsInventory = newOriginsInventoryBuilder(backendService)
                .connectionPoolFactory(simplePoolFactory())
                .build();

        OriginStatsFactory originStatsFactory = mock(OriginStatsFactory.class);

        HttpClient client = factory.createClient(backendService, originsInventory, originStatsFactory);

        assertThat(client, is(instanceOf(StyxHttpClient.class)));

        // note: for more meaningful tests, perhaps we need to add package-private accessor methods to StyxHttpClient
        //       there is also the issue that the Transport class assumes it will get a NettyConnection, so mocks can't be used
        //       these problems are both outside the scope of the current issue being implemented
        //       so for the time being, this test is mostly a placeholder
    }
}