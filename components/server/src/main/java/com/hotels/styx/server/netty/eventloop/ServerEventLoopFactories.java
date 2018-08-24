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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;

import static java.util.Objects.requireNonNull;


/**
 * Static utility methods pertaining to {@code ServerEventLoopFactory} instances.
 */
public final class ServerEventLoopFactories {
    private ServerEventLoopFactories() {
    }

    /**
     * Returns a ServerEventLoopFactory which caches the instance retrieved during the first
     * call to {@code newBossEventLoopGroup}, {@code newWorkerEventLoopGroup} and returns that value on subsequent calls.
     */
    public static ServerEventLoopFactory memoize(ServerEventLoopFactory serverEventLoopFactory) {
        return new MemoizingServerEventLoopFactory(requireNonNull(serverEventLoopFactory));
    }

    private static class MemoizingServerEventLoopFactory implements ServerEventLoopFactory {
        private final EventLoopGroup bossEventExecutors;
        private final EventLoopGroup workerEventExecutors;
        private final Class<? extends ServerChannel> serverChannelClass;

        public MemoizingServerEventLoopFactory(ServerEventLoopFactory delegate) {
            this.bossEventExecutors = delegate.newBossEventLoopGroup();
            this.workerEventExecutors = delegate.newWorkerEventLoopGroup();
            this.serverChannelClass = delegate.serverChannelClass();
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
            return serverChannelClass;
        }
    }

}
