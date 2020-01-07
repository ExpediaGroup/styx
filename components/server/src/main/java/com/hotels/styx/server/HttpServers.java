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
package com.hotels.styx.server;

import com.hotels.styx.IStyxServer;
import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.WebServerConnectorFactory;

import static com.hotels.styx.server.netty.SslContexts.newSSLContext;

/**
 * Static utility methods for creating {@link com.hotels.styx.server.HttpServer} instances.
 */
public class HttpServers {
    /**
     * Returns a new {@link com.hotels.styx.server.HttpServer} object, which runs on the provided port.
     *
     * @param port
     * @return {@link com.hotels.styx.server.HttpServer} object
     */
    public static IStyxServer createHttpServer(int port, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(port, null))
                .handler(handler)
                .workerExecutor(NettyExecutor.create("NettyServer", 1))
                .build();
    }

    /**
     * Returns a new {@link com.hotels.styx.server.HttpServer} object, which runs on the provided port.
     *
     * @param name - Name of the server and associated IO thread.
     * @param httpConnectorConfig - HTTP connector configuration.
     * @param handler - Request handler.
     *
     * @return {@link com.hotels.styx.server.HttpServer} object
     */
    public static IStyxServer createHttpServer(String name, HttpConnectorConfig httpConnectorConfig, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(httpConnectorConfig.port(), null))
                .handler(handler)
                .workerExecutor(NettyExecutor.create(name, 1))
                .build();
    }

    /**
     * Returns a new {@link com.hotels.styx.server.HttpServer} object, using secure HTTPS protocol.
     *
     * @param name - Name of the server and associated IO thread.
     * @param config - HTTPS endpoint configuration.
     * @param handler - Request handler.
     *
     * @return {@link com.hotels.styx.server.HttpServer} object
     */
    public static IStyxServer createHttpsServer(String name, HttpsConnectorConfig config, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(config.port(), newSSLContext(config)))
                .handler(handler)
                .workerExecutor(NettyExecutor.create(name, 1))
                .build();
    }

}
