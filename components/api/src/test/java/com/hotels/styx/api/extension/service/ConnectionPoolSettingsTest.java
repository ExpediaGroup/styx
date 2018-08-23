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
package com.hotels.styx.api.extension.service;

import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ConnectionPoolSettingsTest {
    @Test
    public void setsConfigurationValues() {
        ConnectionPoolSettings config = new ConnectionPoolSettings(5, 8, 2345, 123, 1L);

        assertThat(config.connectTimeoutMillis(), is(equalTo(2345)));
        assertThat(config.pendingConnectionTimeoutMillis(), is(equalTo(123)));
        assertThat(config.maxConnectionsPerHost(), is(equalTo(5)));
        assertThat(config.maxPendingConnectionsPerHost(), is(equalTo(8)));
        assertThat(config.connectionExpirationSeconds(), is(equalTo(1L)));
    }

    @Test
    public void shouldBuildFromOtherPoolSettings() {
        ConnectionPoolSettings config = new ConnectionPoolSettings(5, 8, 2345, 123, 1L);
        ConnectionPoolSettings newConfig = new ConnectionPoolSettings.Builder(config)
                .connectTimeout(444, MILLISECONDS)
                .build();

        assertThat(newConfig.connectTimeoutMillis(), is(equalTo(444)));
        assertThat(newConfig.pendingConnectionTimeoutMillis(), is(equalTo(123)));
        assertThat(newConfig.maxConnectionsPerHost(), is(equalTo(5)));
        assertThat(newConfig.maxPendingConnectionsPerHost(), is(equalTo(8)));
        assertThat(config.connectionExpirationSeconds(), is(equalTo(1L)));
    }
}
