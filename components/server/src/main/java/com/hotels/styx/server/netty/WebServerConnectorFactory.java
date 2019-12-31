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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.netty.codec.NettyToStyxRequestDecoder;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;

/**
 * Creates connectors for web servers.
 */
public class WebServerConnectorFactory implements ServerConnectorFactory {

    @Override
    public ServerConnector create(int port, SslContext sslContext) {
        return new WebServerConnector(port, sslContext);
    }

    private static final class WebServerConnector implements ServerConnector {
        private final int port;
        private final SslContext sslContext;

        private WebServerConnector(int port, SslContext sslContext) {
            this.port = port;
            this.sslContext = sslContext;
        }

        @Override
        public String type() {
            return this.sslContext == null ? "http" : "https";
        }

        @Override
        public int port() {
            return this.port;
        }

        @Override
        public void configure(Channel channel, HttpHandler httpHandler) {
            if (this.sslContext != null) {
                channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
            }

            channel.pipeline()
                    .addLast(new HttpServerCodec())
                    .addLast(new NettyToStyxRequestDecoder.Builder()
                            .flowControlEnabled(true)
                            .build())
                    .addLast(new HttpPipelineHandler.Builder(httpHandler).build());
        }
    }
}
