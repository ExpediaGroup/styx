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

import com.hotels.styx.events.EventNexus;
import com.hotels.styx.server.HttpServer;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NettyServerTest {
    @Test
    public void firesEventWhenStarted() {
        EventNexus eventNexus = mock(EventNexus.class);

        ServerConnector httpConnector = mock(ServerConnector.class);

        HttpServer server = NettyServerBuilder.newBuilder()
                .eventNexus(eventNexus)
                .setHttpConnector(httpConnector)
                .name("testserver")
                .build();

        server.startAsync().awaitRunning();

        verify(eventNexus).publish("server.started.testserver", true);
    }
}
