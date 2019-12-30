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

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.ServerEventLoopFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
final class NettyServer extends AbstractService implements HttpServer {
    private static final Logger LOGGER = getLogger(NettyServer.class);

    private final String host;
    private final ChannelGroup channelGroup;
    private final ServerEventLoopFactory serverEventLoopFactory;

    private final ServerConnector protocolConnector;

    private final HttpHandler handler;
    private final ServerSocketBinder protocolSocketBinder;

    private volatile Callable<?> stopper;
    private volatile HttpHandler httpHandler;

    NettyServer(NettyServerBuilder nettyServerBuilder) {
        this.host = nettyServerBuilder.host();
        this.channelGroup = requireNonNull(nettyServerBuilder.channelGroup());
        this.serverEventLoopFactory = requireNonNull(nettyServerBuilder.serverEventLoopFactory(), "serverEventLoopFactory cannot be null");
        this.handler = requireNonNull(nettyServerBuilder.handler());

        this.protocolConnector = nettyServerBuilder.protocolConnector();

        this.protocolSocketBinder = new ServerSocketBinder(protocolConnector);
    }

    @Override
    public InetSocketAddress httpAddress() {
        return Optional.ofNullable(protocolSocketBinder)
                .map(it -> {
                    try {
                        return new InetSocketAddress(host, it.port());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    @Override
    protected void doStart() {
        LOGGER.info("starting services");

        httpHandler = NettyServer.this.handler;

        ServiceManager serviceManager = new ServiceManager(
                Stream.of(protocolSocketBinder)
                .collect(Collectors.toList())
        );

        serviceManager.addListener(new ServerListener(this));

        this.stopper = () -> {
            serviceManager.stopAsync();
            return null;
        };

        serviceManager.startAsync();
    }

    @Override
    protected void doStop() {
        try {
            LOGGER.info("stopping server");
            this.stopper.call();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    private static final class ServerListener extends ServiceManager.Listener {
        private final NettyServer server;

        public ServerListener(NettyServer server) {
            this.server = server;
        }

        @Override
        public void healthy() {
            server.notifyStarted();
        }

        @Override
        public void stopped() {
            LOGGER.info("stopped");
            server.notifyStopped();
        }

        @Override
        public void failure(Service service) {
            LOGGER.warn("Failed to start service={} cause={}", service, service.failureCause());
            server.notifyFailed(service.failureCause());
        }
    }

    private final class ServerSocketBinder extends AbstractService {
        private final ServerConnector serverConnector;
        private volatile Callable<?> connectorStopper;
        private volatile InetSocketAddress address;

        private ServerSocketBinder(ServerConnector serverConnector) {
            this.serverConnector = serverConnector;
        }

        @Override
        protected void doStart() {
            ServerBootstrap b = new ServerBootstrap();
            EventLoopGroup bossGroup = serverEventLoopFactory.newBossEventLoopGroup();
            EventLoopGroup workerGroup = serverEventLoopFactory.newWorkerEventLoopGroup();
            Class<? extends ServerChannel> channelType = serverEventLoopFactory.serverChannelClass();

            b.group(bossGroup, workerGroup)
                    .channel(channelType)
                    .option(SO_BACKLOG, 1024)
                    .option(SO_REUSEADDR, true)
                    .childOption(SO_REUSEADDR, true)
                    .childOption(SO_KEEPALIVE, true)
                    .childOption(TCP_NODELAY, true)
                    .childOption(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            serverConnector.configure(ch, httpHandler);
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
                            LOGGER.info("server connector {} bound successfully on port {} socket port {}", new Object[] {serverConnector.getClass(), port, address});
                            connectorStopper = new Stopper(bossGroup, workerGroup);
                            notifyStarted();
                        } else {
                            notifyFailed(mapToBetterException(future.cause(), port));
                        }
                    });
        }

        public int port() {
            return (address != null) ? address.getPort() : -1;
        }

        private Throwable mapToBetterException(Throwable cause, int port) {
            if (cause instanceof BindException) {
                return new BindException(format("Address [%s] already is use.", port));
            }
            return cause;
        }

        @Override
        protected void doStop() {
            try {
                if (connectorStopper != null) {
                    connectorStopper.call();
                }
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        private class Stopper implements Callable<Void> {
            private final EventLoopGroup bossGroup;
            private final EventLoopGroup workerGroup;

            public Stopper(EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
                this.bossGroup = bossGroup;
                this.workerGroup = workerGroup;
            }

            @Override
            public Void call() {
                channelGroup.close().awaitUninterruptibly();
                shutdownEventExecutorGroup(bossGroup);
                shutdownEventExecutorGroup(workerGroup);
                notifyStopped();
                return null;
            }

            private Future<?> shutdownEventExecutorGroup(EventExecutorGroup eventExecutorGroup) {
                return eventExecutorGroup.shutdownGracefully(10, 1000, MILLISECONDS);
            }
        }
    }
}
