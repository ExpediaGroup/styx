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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.netty.eventloop.PlatformAwareServerEventLoopFactory;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.server.netty.eventloop.ServerEventLoopFactories.memoize;

/**
 * A builder of {@link NettyServer} instances.
 */
public final class NettyServerBuilder {
    private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private ServerEventLoopFactory serverEventLoopFactory;

    private String host;
    private MetricRegistry metricRegistry;
    private String name = "styx";
    private ServerConnector httpConnector;
    private HttpHandler handler = (request, context) -> Eventual.of(LiveHttpResponse.response(NOT_FOUND).build());

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

    public NettyServerBuilder name(String name) {
        this.name = name;
        return this;
    }

    public NettyServerBuilder setMetricsRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        return this;
    }

    MetricRegistry metricsRegistry() {
        return this.metricRegistry;
    }

    public NettyServerBuilder setServerEventLoopFactory(ServerEventLoopFactory serverEventLoopFactory) {
        this.serverEventLoopFactory = serverEventLoopFactory;
        return this;
    }

    ServerEventLoopFactory serverEventLoopFactory() {
        return firstNonNull(this.serverEventLoopFactory, memoize(new PlatformAwareServerEventLoopFactory(name, 1, 1)));
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

        return new NettyServer(this);
    }
}
