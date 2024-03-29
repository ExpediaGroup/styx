/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.HttpConfig;
import com.hotels.styx.client.HttpRequestOperationFactory;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

public class NettyConnectionTest {
    private Channel channel;
    private Origin origin;
    private HttpConfig httpConfig = HttpConfig.defaultHttpConfig();

    @BeforeEach
    public void startOriginServer() {
        channel = new EmbeddedChannel();
        origin = newOriginBuilder("localhost", 0).build();
    }

    @Test
    public void willNotNotifyListenersIfConnectionRemainActive() throws Exception {
        Connection connection = createConnection();
        EventCapturingListener listener = new EventCapturingListener();
        connection.addConnectionListener(listener);

        assertThat(listener.closedConnection(), isAbsent());
    }

    @Test
    public void notifiesListenersWhenConnectionIsClosed() throws Exception {
        Connection connection = createConnection();

        EventCapturingListener listener = new EventCapturingListener();
        connection.addConnectionListener(listener);

        channel.close();

        assertThat(connection.isConnected(), is(false));
        assertThat(listener.closedConnection(), isValue(connection));
    }

    private Connection createConnection() {
        HttpRequestOperationFactory requestOperationFactory = mock(HttpRequestOperationFactory.class);

        return new NettyConnection(origin, channel, requestOperationFactory, httpConfig, null, false, Optional.empty());
    }

    static class EventCapturingListener implements Connection.Listener {
        private Connection closedConnection;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void connectionClosed(Connection connection) {
            this.closedConnection = connection;
            this.latch.countDown();
        }

        Optional<Connection> closedConnection() {
            return Optional.ofNullable(this.closedConnection);
        }
    }
}
