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
package com.hotels.styx.server.netty;

import com.hotels.styx.IStyxServer;
import com.hotels.styx.ServerExecutor;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Throwables.propagate;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * NettyServer.
 */
final class NettyServer extends AbstractStyxService implements IStyxServer {
    private static final Logger LOGGER = getLogger(NettyServer.class);

    private final ChannelGroup channelGroup;

    private final HttpHandler handler;
    private final ServerConnector serverConnector;
    private final String host;
    private final ServerExecutor bossExecutor;
    private final ServerExecutor workerExecutor;

    private volatile Callable<?> stopper;
    private volatile InetSocketAddress address;

    NettyServer(NettyServerBuilder nettyServerBuilder) {
        // Ideally host:port as a service name:
        super("");
        this.host = nettyServerBuilder.host();
        this.channelGroup = requireNonNull(nettyServerBuilder.channelGroup());
        this.handler = requireNonNull(nettyServerBuilder.handler());
        this.serverConnector = nettyServerBuilder.protocolConnector();
        this.bossExecutor = nettyServerBuilder.bossExecutor();
        this.workerExecutor = nettyServerBuilder.workerExecutor();
    }

    @Override
    public InetSocketAddress inetAddress() {
        return Optional.ofNullable(address)
                .map(it -> {
                    try {
                        return new InetSocketAddress(host, it.getPort());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    @Override
    protected CompletableFuture<Void> startService() {
        LOGGER.info("starting services");

        CompletableFuture<Void> serviceFuture = new CompletableFuture<>();

        ServerBootstrap b = new ServerBootstrap();

        b.group(bossExecutor.eventLoopGroup(), workerExecutor.eventLoopGroup())
                .channel(bossExecutor.getEventLoopClass())
                .option(SO_BACKLOG, 1024)
                .option(SO_REUSEADDR, true)
                .childOption(SO_REUSEADDR, true)
                .childOption(SO_KEEPALIVE, true)
                .childOption(TCP_NODELAY, true)
                .childOption(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        serverConnector.configure(ch, handler);
                    }
                });

        // Bind and start to accept incoming connections.
        int port = serverConnector.port();

        b.bind(new InetSocketAddress(port))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel channel = future.channel();
                        channelGroup.add(channel);
                        address = (InetSocketAddress) channel.localAddress();
                        LOGGER.info("server connector {} bound successfully on port {} socket port {}", new Object[]{serverConnector.getClass(), port, address});
                        stopper = new Stopper(bossExecutor, workerExecutor);
                        serviceFuture.complete(null);
                    } else {
                        LOGGER.warn("Failed to start service={} cause={}", this, future.cause());
                        serviceFuture.completeExceptionally(mapToBetterException(future.cause(), port));
                    }
                });

        return serviceFuture;
    }

    @Override
    protected CompletableFuture<Void> stopService() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (stopper != null) {
                    stopper.call();
                    address = null;
                }
            } catch (Exception e) {
                throw propagate(e);
            }
        });
    }

    private Throwable mapToBetterException(Throwable cause, int port) {
        if (cause instanceof BindException) {
            return new BindException(format("Address [%s] already is use.", port));
        }
        return cause;
    }

    private class Stopper implements Callable<Void> {
        private final ServerExecutor bossGroup;
        private final ServerExecutor workerGroup;

        public Stopper(ServerExecutor bossGroup, ServerExecutor workerGroup) {
            this.bossGroup = bossGroup;
            this.workerGroup = workerGroup;
        }

        @Override
        public Void call() {
            channelGroup.close().awaitUninterruptibly();
            // TODO: Mikko: check why the return value is never used:
            shutdownEventExecutorGroup(bossGroup);
            shutdownEventExecutorGroup(workerGroup);
            return null;
        }

        private Future<?> shutdownEventExecutorGroup(ServerExecutor eventExecutorGroup) {
            return eventExecutorGroup.eventLoopGroup().shutdownGracefully(10, 1000, MILLISECONDS);
        }
    }
}
