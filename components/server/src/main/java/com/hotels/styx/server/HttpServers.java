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

import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.WebServerConnectorFactory;

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
    public static HttpServer createHttpServer(int port, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(new HttpConnectorConfig(port)))
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
    public static HttpServer createHttpServer(String name, HttpConnectorConfig httpConnectorConfig, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(httpConnectorConfig))
                .handler(handler)
                .workerExecutor(NettyExecutor.create(name, 1))
                .build();
    }

    /**
     * Returns a new {@link com.hotels.styx.server.HttpServer} object, using secure HTTPS protocol.
     *
     * @param name - Name of the server and associated IO thread.
     * @param httpsConnectorConfig - HTTPS endpoint configuration.
     * @param handler - Request handler.
     *
     * @return {@link com.hotels.styx.server.HttpServer} object
     */
    public static HttpServer createHttpsServer(String name, HttpsConnectorConfig httpsConnectorConfig, HttpHandler handler) {
        return NettyServerBuilder.newBuilder()
                .setProtocolConnector(new WebServerConnectorFactory().create(httpsConnectorConfig))
                .handler(handler)
                .workerExecutor(NettyExecutor.create(name, 1))
                .build();
    }

}
