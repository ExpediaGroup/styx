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
package com.hotels.styx.server.netty.eventloop.nio;

import com.hotels.styx.server.ServerEventLoopFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import static com.hotels.styx.server.netty.eventloop.nio.NioEventLoopGroups.newEventLoopGroup;


/**
 * A factory for creating Nio based client event loop.
 *
 */
public class NioServerEventLoopGroupFactory implements ServerEventLoopFactory {
    private final EventLoopGroup bossEventExecutors;
    private final EventLoopGroup workerEventExecutors;

    public NioServerEventLoopGroupFactory(String name, int bossThreadsCount, int workerThreadsCount) {
        this.bossEventExecutors = newEventLoopGroup(bossThreadsCount, name + "-Boss-%d-Thread");
        this.workerEventExecutors = newEventLoopGroup(workerThreadsCount, name + "-Worker-%d-Thread");
    }

    @Override
    public EventLoopGroup newBossEventLoopGroup() {
        return bossEventExecutors;
    }


    @Override
    public EventLoopGroup newWorkerEventLoopGroup() {
        return workerEventExecutors;
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass() {
        return NioServerSocketChannel.class;
    }

}
