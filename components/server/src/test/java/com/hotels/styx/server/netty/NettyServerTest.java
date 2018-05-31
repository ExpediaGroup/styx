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
package com.hotels.styx.server.netty;

import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.server.HttpServer;
import org.testng.annotations.Test;

import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class NettyServerTest {
    @Test
    public void notifiesAboutStart() {
        ConfigStore configStore = new ConfigStore();

        ServerConnector httpConnector = mock(ServerConnector.class);

        HttpServer server = NettyServerBuilder.newBuilder()
                .configStore(configStore)
                .setHttpConnector(httpConnector)
                .name("testserver")
                .build();

        server.startAsync().awaitRunning();

        assertThat(configStore.get("server.started.testserver", Boolean.class), isValue(true));
    }
}
