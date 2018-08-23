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
package com.hotels.styx.proxy;

import com.codahale.metrics.Histogram;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.RequestStatsCollector;
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.netty.NettyServerConfig;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.server.netty.ServerConnectorFactory;
import com.hotels.styx.server.netty.codec.NettyToStyxRequestDecoder;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler;
import com.hotels.styx.server.netty.connectors.ResponseEnhancer;
import com.hotels.styx.server.netty.handlers.ChannelStatisticsHandler;
import com.hotels.styx.server.netty.handlers.ExcessConnectionRejector;
import com.hotels.styx.server.netty.handlers.RequestTimeoutHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;

import java.util.Optional;

import static com.hotels.styx.server.netty.SslContexts.newSSLContext;
import static io.netty.handler.timeout.IdleState.ALL_IDLE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Factory for proxy connectors.
 */
class ProxyConnectorFactory implements ServerConnectorFactory {
    private final MetricRegistry metrics;
    private final HttpErrorStatusListener errorStatusListener;
    private final NettyServerConfig serverConfig;
    private final String unwiseCharacters;
    private final ResponseEnhancer responseEnhancer;

    ProxyConnectorFactory(NettyServerConfig serverConfig,
                          MetricRegistry metrics,
                          HttpErrorStatusListener errorStatusListener,
                          String unwiseCharacters,
                          ResponseEnhancer responseEnhancer) {
        this.serverConfig = requireNonNull(serverConfig);
        this.metrics = requireNonNull(metrics);
        this.errorStatusListener = requireNonNull(errorStatusListener);
        this.unwiseCharacters = requireNonNull(unwiseCharacters);
        this.responseEnhancer = requireNonNull(responseEnhancer);
    }

    @Override
    public ServerConnector create(HttpConnectorConfig config) {
        return new ProxyConnector(config, serverConfig, metrics, errorStatusListener, unwiseCharacters, responseEnhancer);
    }

    @Override
    public ServerConnector create(HttpsConnectorConfig config) {
        return new ProxyConnector(config, serverConfig, metrics, errorStatusListener, unwiseCharacters, responseEnhancer);
    }

    private static final class ProxyConnector implements ServerConnector {
        private final HttpConnectorConfig config;
        private final NettyServerConfig serverConfig;
        private final MetricRegistry metrics;
        private final HttpErrorStatusListener httpErrorStatusListener;
        private final ChannelStatisticsHandler channelStatsHandler;
        private final ExcessConnectionRejector excessConnectionRejector;
        private final RequestStatsCollector requestStatsCollector;
        private final ConfigurableUnwiseCharsEncoder unwiseCharEncoder;
        private final Optional<SslContext> sslContext;
        private final ResponseEnhancer responseEnhancer;

        private ProxyConnector(HttpConnectorConfig config,
                               NettyServerConfig serverConfig,
                               MetricRegistry metrics,
                               HttpErrorStatusListener errorStatusListener,
                               String unwiseCharacters,
                               ResponseEnhancer responseEnhancer) {
            this.responseEnhancer = requireNonNull(responseEnhancer);
            this.config = requireNonNull(config);
            this.serverConfig = requireNonNull(serverConfig);
            this.metrics = requireNonNull(metrics);
            this.httpErrorStatusListener = requireNonNull(errorStatusListener);
            this.channelStatsHandler = new ChannelStatisticsHandler(metrics);
            this.requestStatsCollector = new RequestStatsCollector(metrics.scope("requests"));
            this.excessConnectionRejector = new ExcessConnectionRejector(new DefaultChannelGroup(GlobalEventExecutor.INSTANCE), serverConfig.maxConnectionsCount());
            this.unwiseCharEncoder = new ConfigurableUnwiseCharsEncoder(unwiseCharacters);
            if (isHttps()) {
                this.sslContext = Optional.of(newSSLContext((HttpsConnectorConfig) config, metrics));
            } else {
                this.sslContext = Optional.empty();
            }
        }

        @Override
        public String type() {
            return config.type();
        }

        @Override
        public int port() {
            return config.port();
        }

        @Override
        public void configure(Channel channel, HttpHandler httpPipeline) {
            sslContext.ifPresent(ssl -> {
                SslHandler sslHandler = ssl.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
            });

            channel.pipeline()
                    .addLast("connection-throttler", excessConnectionRejector)
                    .addLast("idle-handler", new IdleStateHandler(serverConfig.requestTimeoutMillis(), 0, serverConfig.keepAliveTimeoutMillis(), MILLISECONDS))
                    .addLast("channel-stats", channelStatsHandler)

                    // Http Server Codec
                    .addLast("http-server-codec", new HttpServerCodec(serverConfig.maxInitialLineLength(), serverConfig.maxHeaderSize(), serverConfig.maxChunkSize(), true))

                    // idle-handler and timeout-handler must be before aggregator. Otherwise
                    // timeout handler cannot see the incoming HTTP chunks.
                    .addLast("timeout-handler", new RequestTimeoutHandler())

                    .addLast("keep-alive-handler", new IdleTransactionConnectionCloser(metrics))

                    .addLast("server-protocol-distribution-recorder", new ServerProtocolDistributionRecorder(metrics, sslContext.isPresent()))

                    .addLast("styx-decoder", requestTranslator())

                    .addLast("proxy", new HttpPipelineHandler.Builder(httpPipeline)
                            .responseEnhancer(responseEnhancer)
                            .errorStatusListener(httpErrorStatusListener)
                            .progressListener(requestStatsCollector)
                            .metricRegistry(metrics)
                            .build());
        }


        private NettyToStyxRequestDecoder requestTranslator() {
            return new NettyToStyxRequestDecoder.Builder()
                    .secure(isHttps())
                    .flowControlEnabled(true)
                    .unwiseCharEncoder(unwiseCharEncoder)
                    .build();
        }

        private boolean isHttps() {
            return "https".equals(config.type());
        }

        private static class IdleTransactionConnectionCloser extends ChannelDuplexHandler {
            private static final Logger LOGGER = getLogger(IdleTransactionConnectionCloser.class);
            private final Histogram idleConnectionClosed;
            private final MetricRegistry metricRegistry;

            private volatile boolean httpTransactionOngoing;

            IdleTransactionConnectionCloser(MetricRegistry metricRegistry) {
                this.metricRegistry = metricRegistry.scope("connections");
                this.idleConnectionClosed = this.metricRegistry.histogram("idleClosed");
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
                    httpTransactionOngoing = true;
                }
                super.channelRead(ctx, msg);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof LastHttpContent) {
                    httpTransactionOngoing = false;
                }
                super.write(ctx, msg, promise);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof IdleStateEvent) {
                    IdleStateEvent e = (IdleStateEvent) evt;
                    if (e.state() == ALL_IDLE && !httpTransactionOngoing) {
                        if (ctx.channel().isActive()) {
                            LOGGER.warn("Closing an idle connection={}", ctx.channel().remoteAddress());
                            ctx.close();
                            idleConnectionClosed.update(1);
                        }
                    }
                }
            }
        }
    }
}
