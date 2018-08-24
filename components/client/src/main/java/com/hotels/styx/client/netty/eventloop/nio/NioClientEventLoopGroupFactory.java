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
package com.hotels.styx.client.netty.eventloop.nio;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hotels.styx.api.netty.ClientEventLoopFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import static java.util.Objects.requireNonNull;


/**
 * A factory for creating non-blocking-I/O-based client event loops.
 */
public class NioClientEventLoopGroupFactory implements ClientEventLoopFactory {
    private final String name;
    private final int clientWorkerThreadsCount;

    /**
     * Constructs an instance.
     *
     * @param name name to prefix thread names with
     * @param clientWorkerThreadsCount number of client worker threads
     */
    public NioClientEventLoopGroupFactory(String name, int clientWorkerThreadsCount) {
        this.name = requireNonNull(name);
        this.clientWorkerThreadsCount = requireNonNull(clientWorkerThreadsCount);
    }

    @Override
    public EventLoopGroup newClientWorkerEventLoopGroup() {
        return new NioEventLoopGroup(clientWorkerThreadsCount, new ThreadFactoryBuilder()
                .setNameFormat(name + "-Client-Worker-%d-Thread")
                .build());
    }

    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass() {
        return NioSocketChannel.class;
    }
}
