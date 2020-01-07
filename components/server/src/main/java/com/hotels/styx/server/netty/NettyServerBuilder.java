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
package com.hotels.styx.server.netty;

import com.hotels.styx.NettyExecutor;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.HttpServer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.LiveHttpResponse.response;

/**
 * A builder of {@link NettyServer} instances.
 */
public final class NettyServerBuilder {
    private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    private String host;
    private MetricRegistry metricRegistry;
    private ServerConnector httpConnector;
    private HttpHandler handler = (request, context) -> Eventual.of(response(NOT_FOUND).build());
    private NettyExecutor bossExecutor;
    private NettyExecutor workerExecutor;

    public static NettyServerBuilder newBuilder() {
        return new NettyServerBuilder();
    }

    String host() {
        return firstNonNull(host, "localhost");
    }

    public NettyServerBuilder host(String host) {
        this.host = host;
        return this;
    }

    public NettyServerBuilder setMetricsRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        return this;
    }

    MetricRegistry metricsRegistry() {
        return this.metricRegistry;
    }

    public NettyServerBuilder bossExecutor(NettyExecutor executor) {
        this.bossExecutor = checkNotNull(executor, "boss executor");
        return this;
    }

    public NettyServerBuilder workerExecutor(NettyExecutor executor) {
        this.workerExecutor = checkNotNull(executor, "worker executor");
        return this;
    }

    public NettyExecutor bossExecutor() {
        return this.bossExecutor;
    }

    public NettyExecutor workerExecutor() {
        return this.workerExecutor;
    }

    public ChannelGroup channelGroup() {
        return this.channelGroup;
    }

    public NettyServerBuilder handler(HttpHandler handler) {
        this.handler = handler;
        return this;
    }

    HttpHandler handler() {
        return this.handler;
    }

    public NettyServerBuilder setProtocolConnector(ServerConnector connector) {
        this.httpConnector = connector;
        return this;
    }

    ServerConnector protocolConnector() {
        return httpConnector;
    }

    public HttpServer build() {
        checkArgument(httpConnector != null, "Must configure a protocol connector");
        checkArgument(workerExecutor != null, "Must configure a worker executor");

        if (bossExecutor == null) {
            bossExecutor = NettyExecutor.create("Server-Boss", 1);
        }
        return new NettyServer(this);
    }
}
