/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hotels.styx.EventLoopGroups.epollEventLoopGroup;
import static com.hotels.styx.EventLoopGroups.nioEventLoopGroup;

/**
 * A netty based executor for styx.
 */
public class NettyExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(NettyExecutor.class);

    private final Class<? extends ServerChannel> serverEventLoopClass;
    private final Class<? extends SocketChannel> clientEventLoopClass;
    private final EventLoopGroup eventLoopGroup;

    /**
     * Constructs an netty/io event executor.
     * @param name thread group name.
     * @param count thread count.
     * @return
     */
    public static NettyExecutor create(String name, int count) {
        if (Epoll.isAvailable()) {
            LOG.info("Epoll is available. Using the native socket transport.");
            return new NettyExecutor(
                    epollEventLoopGroup(count, name + "-%d-Thread"),
                    EpollServerSocketChannel.class,
                    EpollSocketChannel.class);
        } else {
            LOG.info("Epoll not available. Using nio socket transport.");
            return new NettyExecutor(
                    nioEventLoopGroup(count, name + "-%d-Thread"),
                    NioServerSocketChannel.class,
                    NioSocketChannel.class);
        }
    }

    private NettyExecutor(EventLoopGroup eventLoopGroup,
                         Class<? extends ServerChannel> serverEventLoopClass,
                         Class<? extends SocketChannel> clientEventLoopClass) {
            this.serverEventLoopClass = serverEventLoopClass;
            this.clientEventLoopClass = clientEventLoopClass;
            this.eventLoopGroup = eventLoopGroup;
    }

    public void shut() {
        eventLoopGroup.shutdownGracefully();
    }

    public Class<? extends ServerChannel> serverEventLoopClass() {
        return serverEventLoopClass;
    }

    public Class<? extends SocketChannel> clientEventLoopClass() {
        return clientEventLoopClass;
    }

    public EventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }

}
