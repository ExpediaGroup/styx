/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.server

import com.hotels.styx.InetServer
import com.hotels.styx.NettyExecutor
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.server.netty.NettyServerBuilder.Companion.newBuilder
import com.hotels.styx.server.netty.WebServerConnectorFactory

/**
 * Static utility methods for creating [com.hotels.styx.server.HttpServer] instances.
 */
object HttpServers {
    /**
     * Returns a new [com.hotels.styx.server.HttpServer] object, which runs on the provided port.
     *
     * @param port
     * @return [com.hotels.styx.server.HttpServer] object
     */
    @JvmStatic
    fun createHttpServer(port: Int, handler: HttpHandler): InetServer = newBuilder()
        .setProtocolConnector(WebServerConnectorFactory().create(HttpConnectorConfig(port)))
        .handler(handler)
        .workerExecutor(NettyExecutor.create("NettyServer", 1))
        .build()

    /**
     * Returns a new [com.hotels.styx.server.HttpServer] object, which runs on the provided port.
     *
     * @param name - Name of the server and associated IO thread.
     * @param httpConnectorConfig - HTTP connector configuration.
     * @param handler - Request handler.
     *
     * @return [com.hotels.styx.server.HttpServer] object
     */
    @JvmStatic
    fun createHttpServer(name: String, httpConnectorConfig: HttpConnectorConfig, handler: HttpHandler): InetServer = newBuilder()
        .setProtocolConnector(WebServerConnectorFactory().create(httpConnectorConfig))
        .handler(handler)
        .workerExecutor(NettyExecutor.create(name, 1))
        .build()

    /**
     * Returns a new [com.hotels.styx.server.HttpServer] object, using secure HTTPS protocol.
     *
     * @param name - Name of the server and associated IO thread.
     * @param httpsConnectorConfig - HTTPS endpoint configuration.
     * @param handler - Request handler.
     *
     * @return [com.hotels.styx.server.HttpServer] object
     */
    @JvmStatic
    fun createHttpsServer(name: String, httpsConnectorConfig: HttpsConnectorConfig, handler: HttpHandler): InetServer = newBuilder()
        .setProtocolConnector(WebServerConnectorFactory().create(httpsConnectorConfig))
        .handler(handler)
        .workerExecutor(NettyExecutor.create(name, 1))
        .build()
}