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
package com.hotels.styx.server.netty;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.netty.codec.NettyToStyxRequestDecoder;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import static com.hotels.styx.server.netty.SslContexts.newSSLContext;
import static java.util.Objects.requireNonNull;

/**
 * Creates connectors for web servers.
 */
public class WebServerConnectorFactory implements ServerConnectorFactory {
    @Override
    public ServerConnector create(HttpConnectorConfig config) {
        return new WebServerConnector(config);
    }

    @Override
    public ServerConnector create(HttpsConnectorConfig config) {
        return new WebServerConnector(config);
    }

    private static final class WebServerConnector implements ServerConnector {
        private final HttpConnectorConfig config;

        private WebServerConnector(HttpConnectorConfig config) {
            this.config = requireNonNull(config);
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
        public void configure(Channel channel, HttpHandler httpHandler) {
            if (isHttps()) {
                channel.pipeline().addLast(sslHandler(channel));
            }

            channel.pipeline()
                    .addLast(new HttpServerCodec())
                    .addLast(new NettyToStyxRequestDecoder.Builder()
                            .flowControlEnabled(true)
                            .build())
                    .addLast(new HttpPipelineHandler.Builder(httpHandler).build());
        }

        private SslHandler sslHandler(Channel channel) {
            SslContext sslContext = newSSLContext((HttpsConnectorConfig) config);

            return sslContext.newHandler(channel.alloc());
        }

        private boolean isHttps() {
            return "https".equals(config.type());
        }
    }
}
