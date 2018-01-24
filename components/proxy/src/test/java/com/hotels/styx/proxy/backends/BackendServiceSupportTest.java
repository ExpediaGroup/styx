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
package com.hotels.styx.proxy.backends;

import com.hotels.styx.client.RewriteConfig;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.connectionpool.ConnectionPoolSettings;
import com.hotels.styx.client.ssl.TlsSettings;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.hotels.styx.api.client.Origin.newOriginBuilder;
import static com.hotels.styx.client.connectionpool.ConnectionPoolSettings.defaultSettableConnectionPoolSettings;
import static com.hotels.styx.client.stickysession.StickySessionConfig.newStickySessionConfigBuilder;
import static com.hotels.styx.proxy.backends.BackendServiceSupport.clientConfigurationChanged;
import static com.hotels.styx.proxy.backends.BackendServiceSupport.networkConfigurationChanged;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BackendServiceSupportTest {

    @Test(dataProvider = "networkConfigurationChanges")
    public void detectsChangesToNetworkSettings(String description, BackendService old, BackendService nueve, Boolean outcome) {
        assertThat(networkConfigurationChanged(old, nueve), is(outcome));
    }

    @Test(dataProvider = "clientConfigurationChanges")
    public void detectsChangesToClientConfig(String description, BackendService old, BackendService nueve, Boolean outcome) {
        assertThat(clientConfigurationChanged(old, nueve), is(outcome));
    }

    @Test
    public void detectsAddedOrigins() {
        BackendService old = new BackendService.Builder()
                .origins(newOriginBuilder("localhost", 8080).id("localhost-8080").build())
                .build();

        BackendService nueve = new BackendService.Builder()
                .origins(
                        newOriginBuilder("localhost", 8080).id("localhost-8080").build(),
                        newOriginBuilder("localhost", 8081).id("localhost-8081").build())
                .build();

        assertThat(BackendServiceSupport.originConfigurationChanged(old, nueve), is(true));
    }

    @Test
    public void detectsModifiedOrigins() {
        BackendService old = new BackendService.Builder()
                .origins(newOriginBuilder("localhost", 8080).build())
                .build();

        BackendService nueve = new BackendService.Builder()
                .origins(newOriginBuilder("localhost", 8081).build())
                .build();

        assertThat(BackendServiceSupport.originConfigurationChanged(old, nueve), is(true));
    }

    @Test
    public void detectsRemovedOrigins() {
        BackendService old = new BackendService.Builder()
                .origins(
                        newOriginBuilder("localhost", 8080).id("localhost-8080").build(),
                        newOriginBuilder("localhost", 8081).id("localhost-8081").build())
                .build();

        BackendService nueve = new BackendService.Builder()
                .origins(newOriginBuilder("localhost", 8080).id("localhost-8080").build())
                .build();

        assertThat(BackendServiceSupport.originConfigurationChanged(old, nueve), is(true));

    }

    @DataProvider(name = "networkConfigurationChanges")
    public static Object[][] networkConfigurationChanges() {
        return new Object[][]{
                {
                        "ConnectionPool changed",
                        new BackendService.Builder()
                                .connectionPoolConfig(defaultSettableConnectionPoolSettings())
                                .build(),
                        new BackendService.Builder()
                                .connectionPoolConfig(
                                        new ConnectionPoolSettings.Builder(defaultSettableConnectionPoolSettings())
                                                .maxConnectionsPerHost(5)
                                                .build())
                                .build(),
                        true
                },
                {
                        "TlsSettings changed",
                        new BackendService.Builder()
                                .https(new TlsSettings.Builder().build())
                                .build(),
                        new BackendService.Builder()
                                .https(new TlsSettings.Builder().trustStorePassword("foobar").build())
                                .build(),
                        true
                },
                {
                        "Network settings unchanged",
                        new BackendService.Builder().build(),
                        new BackendService.Builder().build(),
                        false
                }
        };
    }

    @DataProvider(name = "clientConfigurationChanges")
    public static Object[][] clientConfigurationChanges() {
        return new Object[][]{
                {
                        "Rewrites changed",
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar"))
                                .build(),
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar2"))
                                .build(),
                        true
                },
                {
                        "Rewrites added",
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar"))
                                .build(),
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar"), new RewriteConfig("foo2", "bar2"))
                                .build(),
                        true
                },
                {
                        "Rewrites removed",
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar"), new RewriteConfig("foo2", "bar2"))
                                .build(),
                        new BackendService.Builder()
                                .rewrites(new RewriteConfig("foo", "bar"))
                                .build(),
                        true
                },
                {
                        "StickySession changed",
                        new BackendService.Builder().stickySessionConfig(newStickySessionConfigBuilder().build()).build(),
                        new BackendService.Builder().stickySessionConfig(newStickySessionConfigBuilder().timeout(100, TimeUnit.HOURS).build()).build(),
                        true
                },
                {
                        "ResponseTimeout changed",
                        new BackendService.Builder().responseTimeoutMillis(10).build(),
                        new BackendService.Builder().responseTimeoutMillis(11).build(),
                        true
                },
                {
                        "Nothing changed",
                        new BackendService.Builder().build(),
                        new BackendService.Builder().build(),
                        false
                }
        };
    }
}