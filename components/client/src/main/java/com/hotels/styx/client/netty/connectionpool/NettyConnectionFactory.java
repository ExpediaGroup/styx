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
package com.hotels.styx.client.netty.connectionpool;

import com.google.common.net.HostAndPort;
import com.hotels.styx.client.Connection;
import com.hotels.styx.client.ConnectionSettings;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.netty.ClientEventLoopFactory;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.client.ChannelOptionSetting;
import com.hotels.styx.client.HttpConfig;
import com.hotels.styx.client.HttpRequestOperationFactory;
import com.hotels.styx.client.netty.eventloop.PlatformAwareClientEventLoopGroupFactory;
import com.hotels.styx.client.ssl.SslContextFactory;
import com.hotels.styx.api.extension.service.TlsSettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;
import rx.Observable;

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
    private final ClientEventLoopFactory eventLoopFactory;
    private final HttpConfig httpConfig;
    private final SslContext sslContext;
    private final HttpRequestOperationFactory httpRequestOperationFactory;
    private Bootstrap bootstrap;

    private NettyConnectionFactory(Builder builder) {
        this.eventLoopFactory = new PlatformAwareClientEventLoopGroupFactory(builder.name, builder.clientWorkerThreadsCount);
        this.httpConfig = requireNonNull(builder.httpConfig);
        this.sslContext = builder.tlsSettings == null ? null : SslContextFactory.get(builder.tlsSettings);

        this.httpRequestOperationFactory = requireNonNull(builder.httpRequestOperationFactory);
    }

    @Override
    public Observable<Connection> createConnection(Origin origin, ConnectionSettings connectionSettings) {
        return Observable.create(subscriber -> {
            ChannelFuture channelFuture = openConnection(origin, connectionSettings);

            channelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    subscriber.onNext(new NettyConnection(origin, channelFuture.channel(), httpRequestOperationFactory));
                    subscriber.onCompleted();
                } else {
                    subscriber.onError(new OriginUnreachableException(origin, future.cause()));
                }
            });
        });
    }

    private ChannelFuture openConnection(Origin origin, ConnectionSettings connectionSettings) {
        bootstrap(connectionSettings);
        HostAndPort host = origin.host();
        return bootstrap.connect(host.getHostText(), host.getPort());
    }

    private synchronized void bootstrap(ConnectionSettings connectionSettings) {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
            bootstrap.group(eventLoopFactory.newClientWorkerEventLoopGroup())
                    .channel(eventLoopFactory.clientSocketChannelClass())
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
            ChannelPipeline pipeline = ch.pipeline();

            if (sslContext != null) {
                pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
            }

            pipeline.addLast("http-codec", new HttpClientCodec(httpConfig.maxInitialLineLength(), httpConfig.maxHeadersSize(), httpConfig.maxChunkSize()));
            if (httpConfig.compress()) {
                pipeline.addLast("decompressor", new HttpContentDecompressor());
            }
        }
    }

    /**
     * Builder.
     */
    public static final class Builder {
        private HttpRequestOperationFactory httpRequestOperationFactory = httpRequestOperationFactoryBuilder().build();
        private String name = "Styx-Client";
        private int clientWorkerThreadsCount = 1;
        private HttpConfig httpConfig = defaultHttpConfig();
        private TlsSettings tlsSettings;

        /**
         * Sets the name.
         *
         * @param name name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        /**
         * Sets number of client worker threads.
         *
         * @param clientWorkerThreadsCount number of client worker threads
         * @return this builder
         */
        public Builder clientWorkerThreadsCount(int clientWorkerThreadsCount) {
            this.clientWorkerThreadsCount = clientWorkerThreadsCount;
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
