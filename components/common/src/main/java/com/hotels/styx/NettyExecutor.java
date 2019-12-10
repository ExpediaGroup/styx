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

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

/**
 * A network executor for styx.
 */
public final class NettyExecutor {
    private final Class<? extends Channel> eventLoopClass;
    private final EventLoopGroup eventLoopGroup;

    /**
     * Creates a NettyExecutor instance.
     * @param name thread name
     * @param count thread count
     * @return a NettyExecutor instance
     */
    public static NettyExecutor create(String name, int count) {
        PlatformAwareClientEventLoopGroupFactory factory = new PlatformAwareClientEventLoopGroupFactory(name, count);
        return new NettyExecutor(factory.clientSocketChannelClass(), factory.newClientWorkerEventLoopGroup());
    }

    NettyExecutor(Class<? extends Channel> eventLoopClass, EventLoopGroup eventLoopGroup) {
        this.eventLoopClass = eventLoopClass;
        this.eventLoopGroup = eventLoopGroup;
    }

    public void shut() {
        eventLoopGroup.shutdownGracefully();
    }

    public Class<? extends Channel> getEventLoopClass() {
        return eventLoopClass;
    }

    public EventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }
}
