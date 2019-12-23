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
package com.hotels.styx;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hotels.styx.EventLoopGroups.nioEventLoopGroup;

/**
 * A network executor for styx.
 */
public final class ClientExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(ClientExecutor.class);

    private final Class<? extends SocketChannel> eventLoopClass;
    /**
     * Creates a NettyExecutor instance.
     * @param name thread name
     * @param count thread count
     * @return a NettyExecutor instance
     */
    public static ClientExecutor create(String name, int count) {
        if (Epoll.isAvailable()) {
            LOG.info("Epoll is available. Using the native socket transport.");
            return new ClientExecutor(EpollSocketChannel.class, EventLoopGroups.epollEventLoopGroup(count, name + "-%d-Thread"));
        } else {
            LOG.info("Epoll not available. Using nio socket transport.");
            return new ClientExecutor(NioSocketChannel.class, nioEventLoopGroup(count, name + "-%d-Thread"));
        }
    }

    private final EventLoopGroup eventLoopGroup;

    ClientExecutor(Class<? extends SocketChannel> eventLoopClass, EventLoopGroup eventLoopGroup) {
        this.eventLoopClass = eventLoopClass;
        this.eventLoopGroup = eventLoopGroup;
    }

    public void shut() {
        eventLoopGroup.shutdownGracefully();
    }

    public Class<? extends SocketChannel> getEventLoopClass() {
        return eventLoopClass;
    }

    public EventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }
}
