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
package com.hotels.styx.client.netty.eventloop.epoll;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hotels.styx.api.netty.ClientEventLoopFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;

import static com.hotels.styx.common.MorePreconditions.checkArgument;
import static com.hotels.styx.common.MorePreconditions.checkNotEmpty;

/**
 * A factory for creating Epoll-based client event loops.
 */
public class EpollClientEventLoopGroupFactory implements ClientEventLoopFactory {
    private final String name;
    private final int clientWorkerThreadsCount;

    /**
     * Creates a factory.
     *
     * @param name name to attach to threads
     * @param clientWorkerThreadsCount number of threads
     */
    public EpollClientEventLoopGroupFactory(String name, int clientWorkerThreadsCount) {
        this.name = checkNotEmpty(name);
        this.clientWorkerThreadsCount = checkArgument(clientWorkerThreadsCount, clientWorkerThreadsCount > -1);
    }

    @Override
    public EventLoopGroup newClientWorkerEventLoopGroup() {
        return new EpollEventLoopGroup(clientWorkerThreadsCount, new ThreadFactoryBuilder()
                .setNameFormat(name + "-Client-Worker-%d-Thread")
                .build());
    }

    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass() {
        return EpollSocketChannel.class;
    }
}
