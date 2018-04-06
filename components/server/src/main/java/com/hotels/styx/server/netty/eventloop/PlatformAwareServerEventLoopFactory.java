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
package com.hotels.styx.server.netty.eventloop;

import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.netty.eventloop.epoll.EpollServerEventLoopGroupFactory;
import com.hotels.styx.server.netty.eventloop.nio.NioServerEventLoopGroupFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory that creates platform aware server event loops.
 */
public class PlatformAwareServerEventLoopFactory implements ServerEventLoopFactory {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformAwareServerEventLoopFactory.class);
    private final ServerEventLoopFactory delegate;

    public PlatformAwareServerEventLoopFactory(String name, int bossThreadsCount, int workerThreadsCount) {
        if (Epoll.isAvailable()) {
            LOG.info("Epoll is available so using the native socket transport.");
            delegate = new EpollServerEventLoopGroupFactory(name, bossThreadsCount, workerThreadsCount);
        } else {
            LOG.info("Epoll not available Using nio socket transport.");
            delegate = new NioServerEventLoopGroupFactory(name, bossThreadsCount, workerThreadsCount);
        }
    }

    @Override
    public EventLoopGroup newBossEventLoopGroup() {
        return delegate.newBossEventLoopGroup();
    }

    @Override
    public EventLoopGroup newWorkerEventLoopGroup() {
        return delegate.newWorkerEventLoopGroup();
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass() {
        return delegate.serverChannelClass();
    }
}
