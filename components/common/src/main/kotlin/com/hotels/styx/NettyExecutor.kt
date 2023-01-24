/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx

import com.hotels.styx.EventLoopGroups.epollEventLoopGroup
import com.hotels.styx.EventLoopGroups.nioEventLoopGroup
import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.LoggerFactory.getLogger
import java.lang.Thread.currentThread
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A netty based executor for styx.
 */
class NettyExecutor private constructor(
    @get:JvmName("eventLoopGroup")
    val eventLoopGroup: EventLoopGroup,
    @get:JvmName("serverEventLoopClass")
    val serverEventLoopClass: Class<out ServerChannel>,
    @get:JvmName("clientEventLoopClass")
    val clientEventLoopClass: Class<out SocketChannel>
) {
    fun shut() {
        try {
            eventLoopGroup.shutdownGracefully(0, 0, SECONDS).await(5000)
        } catch (e: InterruptedException) {
            currentThread().interrupt()
            throw e
        }
    }

    companion object {
        private val LOG = getLogger(NettyExecutor::class.java)

        /**
         * Constructs an netty/io event executor.
         *
         * @param name  thread group name.
         * @param count thread count.
         * @return executor
         */
        @JvmStatic
        fun create(name: String, count: Int): NettyExecutor =
            if (Epoll.isAvailable()) {
                LOG.debug("Epoll is available. Using the native socket transport.")
                NettyExecutor(
                    epollEventLoopGroup(count, "$name-%d-Thread"),
                    EpollServerSocketChannel::class.java,
                    EpollSocketChannel::class.java
                )
            } else {
                LOG.debug("Epoll not available. Using nio socket transport.")
                NettyExecutor(
                    nioEventLoopGroup(count, "$name-%d-Thread"),
                    NioServerSocketChannel::class.java,
                    NioSocketChannel::class.java
                )
            }
    }
}
