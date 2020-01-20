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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.TlsSettings;
import com.hotels.styx.client.ChannelOptionSetting;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.client.HttpConfig;
import com.hotels.styx.client.HttpRequestOperationFactory;
import com.hotels.styx.client.ssl.SslContextFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.SslContext;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.hotels.styx.client.HttpConfig.defaultHttpConfig;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static java.util.Objects.requireNonNull;

/**
 * A connection factory that creates connections using netty.
 */
public class NettyConnectionFactory implements Connection.Factory {

    private final HttpConfig httpConfig;
    private final SslContext sslContext;
    private final boolean sendSni;
    private final Optional<String> sniHost;
    private final HttpRequestOperationFactory httpRequestOperationFactory;
    private final NettyExecutor executor;
    private Bootstrap bootstrap;

    private NettyConnectionFactory(Builder builder) {
        this.executor = requireNonNull(builder.executor);
        this.httpConfig = requireNonNull(builder.httpConfig);
        this.sslContext = builder.tlsSettings == null ? null : SslContextFactory.get(builder.tlsSettings);
        this.httpRequestOperationFactory = requireNonNull(builder.httpRequestOperationFactory);
        this.sendSni = builder.tlsSettings != null && builder.tlsSettings.sendSni();
        this.sniHost = builder.tlsSettings != null ? builder.tlsSettings.sniHost() : Optional.empty();
    }

    @Override
    public Mono<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings) {
        return createConnection(origin, connectionSettings, sslContext);
    }

    public Mono<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings, SslContext sslContext) {
        return Mono.create(sink -> {
            ChannelFuture channelFuture = openConnection(origin, connectionSettings);

            channelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    sink.success(new NettyConnection(origin, channelFuture.channel(), httpRequestOperationFactory,
                            httpConfig, sslContext, sendSni, sniHost));
                } else {
                    sink.error(new OriginUnreachableException(origin, future.cause()));
                }
            });
        });
    }

    private ChannelFuture openConnection(Origin origin, ConnectionSettings connectionSettings) {
        bootstrap(connectionSettings);
        return bootstrap.connect(origin.host(), origin.port());
    }

    private synchronized void bootstrap(ConnectionSettings connectionSettings) {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
            bootstrap.group(executor.eventLoopGroup())
                    .channel(executor.clientEventLoopClass())
                    .handler(new Initializer())
                    .option(TCP_NODELAY, true)
                    .option(SO_KEEPALIVE, true)
                    .option(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(CONNECT_TIMEOUT_MILLIS, connectionSettings.connectTimeoutMillis());
            for (ChannelOptionSetting setting : httpConfig.channelSettings()) {
                bootstrap.option(setting.option(), setting.value());
            }
        }
    }

    private class Initializer extends ChannelInitializer<Channel> {
        @Override
        protected void initChannel(Channel ch) {
        }
    }

    /**
     * Builder.
     */
    public static final class Builder {
        private static final NettyExecutor DEFAULT_EXECUTOR = NettyExecutor.create("Netty-Executor", 0);

        private HttpRequestOperationFactory httpRequestOperationFactory = httpRequestOperationFactoryBuilder().build();
        private HttpConfig httpConfig = defaultHttpConfig();
        private TlsSettings tlsSettings;
        private NettyExecutor executor = DEFAULT_EXECUTOR;

        public Builder executor(NettyExecutor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets HTTP configuration settings. Uses default settings if not called.
         *
         * @param httpConfig HTTP configuration settings
         * @return this builder
         */
        public Builder httpConfig(HttpConfig httpConfig) {
            this.httpConfig = requireNonNull(httpConfig);
            return this;
        }

        /**
         * Sets the SSL settings. If not set, non-SSL connections are made.
         *
         * @param tlsSettings SSL settings
         * @return this builder
         */
        public Builder tlsSettings(TlsSettings tlsSettings) {
            this.tlsSettings = tlsSettings;
            return this;
        }

        public Builder httpRequestOperationFactory(HttpRequestOperationFactory httpRequestOperationFactory) {
            this.httpRequestOperationFactory = httpRequestOperationFactory;
            return this;
        }

        public NettyConnectionFactory build() {
            return new NettyConnectionFactory(this);
        }
    }
}
