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
package com.hotels.styx.server.netty

import com.hotels.styx.InetServer
import com.hotels.styx.NettyExecutor
import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.api.MetricRegistry
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.ImmediateEventExecutor

/**
 * A builder of [NettyServer] instances.
 */
class NettyServerBuilder {
    private val channelGroup: ChannelGroup = DefaultChannelGroup(ImmediateEventExecutor.INSTANCE)
    private var host: String? = null
    private var metricRegistry: MetricRegistry? = null
    private var httpConnector: ServerConnector? = null
    private var handler = HttpHandler { _, _ ->
        Eventual.of(LiveHttpResponse.response(NOT_FOUND).build())
    }
    private var bossExecutor: NettyExecutor = DEFAULT_SERVER_BOSS_EXECUTOR
    private var workerExecutor: NettyExecutor? = null
    private var shutdownAction = Runnable {}

    fun host(): String = host ?: "localhost"

    fun metricsRegistry(): MetricRegistry? = metricRegistry

    fun bossExecutor(): NettyExecutor = bossExecutor

    fun workerExecutor(): NettyExecutor? = workerExecutor

    fun channelGroup(): ChannelGroup = channelGroup

    fun shutdownAction(): Runnable = shutdownAction

    fun handler(): HttpHandler = handler

    fun protocolConnector(): ServerConnector? = httpConnector

    fun host(host: String?): NettyServerBuilder = apply {
        this.host = host
    }

    fun setMetricsRegistry(metricRegistry: MetricRegistry?): NettyServerBuilder = apply {
        this.metricRegistry = metricRegistry
    }

    fun bossExecutor(executor: NettyExecutor): NettyServerBuilder = apply {
        bossExecutor = executor
    }

    fun workerExecutor(executor: NettyExecutor): NettyServerBuilder = apply {
        workerExecutor = executor
    }

    fun handler(handler: HttpHandler): NettyServerBuilder = apply {
        this.handler = handler
    }

    fun setProtocolConnector(connector: ServerConnector?): NettyServerBuilder = apply {
        httpConnector = connector
    }

    fun shutdownAction(shutdownAction: Runnable): NettyServerBuilder = apply {
        this.shutdownAction = shutdownAction
    }

    fun build(): InetServer {
        requireNotNull(httpConnector) { "Must configure a protocol connector" }
        requireNotNull(workerExecutor) { "Must configure a worker executor" }

        return NettyServer(this)
    }

    companion object {
        private val DEFAULT_SERVER_BOSS_EXECUTOR = NettyExecutor.create("Server-Boss", 1)

        @JvmStatic
        fun newBuilder() = NettyServerBuilder()
    }
}
