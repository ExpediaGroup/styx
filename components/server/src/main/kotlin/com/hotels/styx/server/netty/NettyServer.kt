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
package com.hotels.styx.server.netty

import com.hotels.styx.InetServer
import com.hotels.styx.NettyExecutor
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.extension.service.spi.AbstractStyxService
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption.ALLOCATOR
import io.netty.channel.ChannelOption.SO_BACKLOG
import io.netty.channel.ChannelOption.SO_KEEPALIVE
import io.netty.channel.ChannelOption.SO_REUSEADDR
import io.netty.channel.ChannelOption.TCP_NODELAY
import io.netty.channel.group.ChannelGroup
import org.slf4j.LoggerFactory.getLogger
import java.net.BindException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.runAsync

/**
 * NettyServer.
 */
internal class NettyServer(nettyServerBuilder: NettyServerBuilder) : AbstractStyxService(""), InetServer {
    private val channelGroup: ChannelGroup
    private val handler: HttpHandler
    private val serverConnector: ServerConnector?
    private val host: String
    private val bossExecutor: NettyExecutor
    private val workerExecutor: NettyExecutor?
    private val shutdownAction: Runnable?

    @Volatile
    private var address: InetSocketAddress? = null

    init {
        host = nettyServerBuilder.host()
        channelGroup = requireNotNull(nettyServerBuilder.channelGroup())
        handler = requireNotNull(nettyServerBuilder.handler())
        serverConnector = nettyServerBuilder.protocolConnector()
        bossExecutor = nettyServerBuilder.bossExecutor()
        workerExecutor = nettyServerBuilder.workerExecutor()
        shutdownAction = nettyServerBuilder.shutdownAction()
    }

    override fun adminInterfaceHandlers(namespace: String): Map<String, HttpHandler> {
        return mapOf(
            "port" to HttpHandler { _, _ ->
                Eventual.of(
                    HttpResponse.response(OK)
                        .disableCaching()
                        .body("${address!!.port}", UTF_8)
                        .build()
                        .stream()
                )
            })
    }

    override fun inetAddress() = address?.let {
        try {
            InetSocketAddress(host, it.port)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun startService(): CompletableFuture<Void> {
        LOGGER.debug("starting services")
        val serviceFuture = CompletableFuture<Void>()
        val b = ServerBootstrap()
        b.group(bossExecutor.eventLoopGroup, workerExecutor!!.eventLoopGroup)
            .channel(bossExecutor.serverEventLoopClass)
            .option(SO_BACKLOG, 1024)
            .option(SO_REUSEADDR, true)
            .childOption(SO_REUSEADDR, true)
            .childOption(SO_KEEPALIVE, true)
            .childOption(TCP_NODELAY, true)
            .childOption(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childHandler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) {
                    serverConnector!!.configure(ch, handler)
                }
            })

        // Bind and start to accept incoming connections.
        val port = serverConnector!!.port()
        b.bind(InetSocketAddress(port))
            .addListener(ChannelFutureListener { future: ChannelFuture ->
                if (future.isSuccess) {
                    val channel = future.channel()
                    channelGroup.add(channel)
                    address = channel.localAddress() as InetSocketAddress
                    LOGGER.debug(
                        "server connector {} bound successfully on port {} socket port {}", serverConnector.javaClass, port, address
                    )
                    serviceFuture.complete(null)
                } else {
                    LOGGER.warn("Failed to start service={}", this, future.cause())
                    serviceFuture.completeExceptionally(mapToBetterException(future.cause(), port))
                }
            })
        return serviceFuture
    }

    override fun stopService(): CompletableFuture<Void> = runAsync {
        channelGroup.close().awaitUninterruptibly()
        shutdownAction?.run()
        address = null
    }

    private fun mapToBetterException(cause: Throwable, port: Int) =
        if (cause is BindException) {
            BindException("Address [$port] already in use.")
        } else {
            cause
        }

    companion object {
        private val LOGGER = getLogger(NettyServer::class.java)
    }
}
