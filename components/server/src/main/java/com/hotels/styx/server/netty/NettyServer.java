/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.server.netty;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.api.HttpHandler2;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * NettyServer.
 */
final class NettyServer extends AbstractService implements HttpServer {
    private static final Logger LOGGER = getLogger(NettyServer.class);

    private final String host;
    private final ChannelGroup channelGroup;
    private final ServerEventLoopFactory serverEventLoopFactory;

    private final InetSocketAddress httpAddress;
    private final InetSocketAddress httpsAddress;
    private final Optional<ServerConnector> httpConnector;
    private final Optional<ServerConnector> httpsConnector;

    private final Iterable<Runnable> startupActions;
    private final HttpHandler2 httpHandler;

    private Callable<?> stopper;

    NettyServer(NettyServerBuilder nettyServerBuilder) {
        this.host = nettyServerBuilder.host();
        this.channelGroup = checkNotNull(nettyServerBuilder.channelGroup());
        this.serverEventLoopFactory = checkNotNull(nettyServerBuilder.serverEventLoopFactory(), "serverEventLoopFactory cannot be null");
        this.httpHandler = checkNotNull(nettyServerBuilder.httpHandler());

        this.httpConnector = nettyServerBuilder.httpConnector();
        this.httpsConnector = nettyServerBuilder.httpsConnector();


        this.httpAddress = address(httpConnector);
        this.httpsAddress = address(httpsConnector);

        this.startupActions = nettyServerBuilder.startupActions();
    }

    private InetSocketAddress address(Optional<ServerConnector> connectorConfig) {
        return connectorConfig
                .map(config -> new InetSocketAddress(host, config.port()))
                .orElse(null);
    }

    @Override
    public InetSocketAddress httpAddress() {
        return httpAddress;
    }

    @Override
    public InetSocketAddress httpsAddress() {
        return httpsAddress;
    }

    @Override
    protected void doStart() {
        LOGGER.info("starting services");

        startupActions.forEach(action -> {
            try {
                action.run();
            } catch (Exception e) {
                notifyFailed(e);
            }
        });

        ServiceManager serviceManager = new ServiceManager(serverSocketBinders());

        serviceManager.addListener(new ServerListener(this));
        serviceManager.startAsync().awaitHealthy();

        this.stopper = () -> {
            serviceManager.stopAsync().awaitStopped();
            return null;
        };
    }

    private List<ServerSocketBinder> serverSocketBinders() {
        return Stream.of(httpConnector, httpsConnector)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ServerSocketBinder::new)
                    .collect(toList());
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
                    .option(TCP_NODELAY, true)
                    .option(SO_KEEPALIVE, true)
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
                            LOGGER.info("server connector {} bound successfully on port {}", serverConnector.getClass(), port);
                            connectorStopper = new Stopper(bossGroup, workerGroup);
                            notifyStarted();
                        } else {
                            notifyFailed(mapToBetterException(future.cause(), port));
                        }
                    });
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
            public Void call() throws Exception {
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
