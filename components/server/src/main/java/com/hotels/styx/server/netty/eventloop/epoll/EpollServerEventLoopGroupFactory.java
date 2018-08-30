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
package com.hotels.styx.server.netty.eventloop.epoll;

import com.hotels.styx.server.ServerEventLoopFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;

import static com.hotels.styx.common.MorePreconditions.checkArgument;
import static com.hotels.styx.server.netty.eventloop.epoll.EpollEventLoopGroups.newEventLoopGroup;
import static java.util.Objects.requireNonNull;

/**
 * A factory for creating Epoll based server event loop.
 */
public class EpollServerEventLoopGroupFactory implements ServerEventLoopFactory {
    private final String name;
    private final int bossThreadsCount;
    private final int workerThreadsCount;

    public EpollServerEventLoopGroupFactory(String name, int bossThreadsCount, int workerThreadsCount) {
        this.name = requireNonNull(name);
        this.bossThreadsCount = checkArgument(bossThreadsCount, bossThreadsCount > -1);
        this.workerThreadsCount = checkArgument(workerThreadsCount, workerThreadsCount > -1);
    }

    @Override
    public EventLoopGroup newBossEventLoopGroup() {
        return newEventLoopGroup(bossThreadsCount, name + "-Boss-%d-Thread");
    }

    @Override
    public EventLoopGroup newWorkerEventLoopGroup() {
        return newEventLoopGroup(workerThreadsCount, name + "-Worker-%d-Thread");
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass() {
        return EpollServerSocketChannel.class;
    }

}
