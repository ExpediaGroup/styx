/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.InetServer;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import org.slf4j.Logger;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * NettyServer.
 */
final class NettyServer extends AbstractStyxService implements InetServer {
    private static final Logger LOGGER = getLogger(NettyServer.class);

    private final ChannelGroup channelGroup;

    private final HttpHandler handler;
    private final ServerConnector serverConnector;
    private final String host;
    private final NettyExecutor bossExecutor;
    private final NettyExecutor workerExecutor;
    private final Runnable shutdownAction;

    private volatile InetSocketAddress address;

    NettyServer(NettyServerBuilder nettyServerBuilder) {
        super("");
        this.host = nettyServerBuilder.host();
        this.channelGroup = requireNonNull(nettyServerBuilder.channelGroup());
        this.handler = requireNonNull(nettyServerBuilder.handler());
        this.serverConnector = nettyServerBuilder.protocolConnector();
        this.bossExecutor = nettyServerBuilder.bossExecutor();
        this.workerExecutor = nettyServerBuilder.workerExecutor();
        this.shutdownAction = nettyServerBuilder.shutdownAction();
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers(String namespace) {
        return ImmutableMap.of(
                "port", (request, response) -> Eventual.of(
                        response(OK)
                                .disableCaching()
                                .body(format("%d", this.address.getPort()), UTF_8)
                                .build()
                                .stream()
                ));
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
        LOGGER.debug("starting services");

        CompletableFuture<Void> serviceFuture = new CompletableFuture<>();

        ServerBootstrap b = new ServerBootstrap();

        b.group(bossExecutor.eventLoopGroup(), workerExecutor.eventLoopGroup())
                .channel(bossExecutor.serverEventLoopClass())
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
                        LOGGER.debug("server connector {} bound successfully on port {} socket port {}", new Object[]{serverConnector.getClass(), port, address});
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
                channelGroup.close().awaitUninterruptibly();
                if (this.shutdownAction != null) {
                    shutdownAction.run();
                }
                address = null;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Throwable mapToBetterException(Throwable cause, int port) {
        if (cause instanceof BindException) {
            return new BindException(format("Address [%s] already is use.", port));
        }
        return cause;
    }
}
