/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.admin.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.startup.extensions.PluginStatusNotifications;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.startup.ProxyStatusNotifications.notifyProxyStatus;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.CONSTRUCTING;
import static com.hotels.styx.startup.extensions.PluginStatusNotifications.PluginStatus.FAILED_WHILE_CONSTRUCTING;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class StartupStatusHandlerTest {
    @Test
    public void showsStatuses() throws IOException {
        ConfigStore configStore = new ConfigStore();

        PluginStatusNotifications notifications = new PluginStatusNotifications(configStore);
        notifications.notifyPluginStatus("myPlugin1", CONSTRUCTING);
        notifications.notifyPluginStatus("myPlugin2", FAILED_WHILE_CONSTRUCTING);

        notifyProxyStatus(configStore, new ProxyServerConfig.Builder()
                .setHttpConnector(new HttpConnectorConfig(8080))
                .build());

        StartupStatusHandler handler = new StartupStatusHandler(configStore);

        HttpResponse response = waitForResponse(handler.handle(
                get("/").build(), mock(HttpInterceptor.Context.class)));

        assertThat(response.status(), is(OK));

        Map<?, ?> map = new ObjectMapper().readValue(response.bodyAs(UTF_8), Map.class);

        assertThat(map.keySet(), contains("connectors", "plugins"));
        assertThat(map.get("connectors"), is(instanceOf(Map.class)));
        assertThat(map.get("plugins"), is(instanceOf(Map.class)));

        Map<?, ?> connectors = (Map<?, ?>) map.get("connectors");
        assertThat(connectors.size(), is(2));
        assertThat(connectors, hasEntry("http", "incomplete"));
        assertThat(connectors, hasEntry("https", "disabled"));

        Map<?, ?> plugins = (Map<?, ?>) map.get("plugins");

        assertThat(plugins.size(), is(2));
        assertThat(plugins, hasEntry("myPlugin1", "incomplete:constructing"));
        assertThat(plugins, hasEntry("myPlugin2", "failed:constructing"));
    }
}