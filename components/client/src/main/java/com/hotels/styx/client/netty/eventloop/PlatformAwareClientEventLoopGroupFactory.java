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
package com.hotels.styx.client.netty.eventloop;

import com.hotels.styx.api.netty.ClientEventLoopFactory;
import com.hotels.styx.client.netty.eventloop.epoll.EpollClientEventLoopGroupFactory;
import com.hotels.styx.client.netty.eventloop.nio.NioClientEventLoopGroupFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * A factory that creates platform-aware client event loops.
 */
public class PlatformAwareClientEventLoopGroupFactory implements ClientEventLoopFactory {
    private static final Logger LOG = getLogger(PlatformAwareClientEventLoopGroupFactory.class);

    private final ClientEventLoopFactory delegate;

    /**
     * Creates a new factory.
     *
     * @param name name to attach to threads
     * @param clientWorkerThreadsCount number of threads
     */
    public PlatformAwareClientEventLoopGroupFactory(String name, int clientWorkerThreadsCount) {
        if (Epoll.isAvailable()) {
            LOG.info("Epoll is available so using the native socket transport.");
            delegate = new EpollClientEventLoopGroupFactory(name, clientWorkerThreadsCount);
        } else {
            LOG.info("Epoll not available Using nio socket transport.");
            delegate = new NioClientEventLoopGroupFactory(name, clientWorkerThreadsCount);
        }
    }

    @Override
    public EventLoopGroup newClientWorkerEventLoopGroup() {
        return delegate.newClientWorkerEventLoopGroup();
    }

    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass() {
        return delegate.clientSocketChannelClass();
    }
}
