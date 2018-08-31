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

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.netty.eventloop.PlatformAwareServerEventLoopFactory;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newCopyOnWriteArrayList;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.server.netty.eventloop.ServerEventLoopFactories.memoize;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link NettyServer} instances.
 */
public final class NettyServerBuilder {
    private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private ServerEventLoopFactory serverEventLoopFactory;

    private String host;
    private MetricRegistry metricRegistry;
    private HealthCheckRegistry healthCheckRegistry;
    private String name = "styx";
    private Optional<ServerConnector> httpConnector = Optional.empty();
    private Optional<ServerConnector> httpsConnector = Optional.empty();
    private final List<Runnable> startupActions = newCopyOnWriteArrayList();
    private HttpHandler httpHandler = (request, context) -> StyxObservable.of(HttpResponse.response(NOT_FOUND).build());

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

    public NettyServerBuilder setHealthCheckRegistry(HealthCheckRegistry healthCheckRegistry) {
        this.healthCheckRegistry = healthCheckRegistry;
        return this;
    }

    HealthCheckRegistry healthCheckRegistry() {
        return this.healthCheckRegistry;
    }

    public NettyServerBuilder register(String name, HealthCheck healthCheck) {
        this.healthCheckRegistry.register(name, requireNonNull(healthCheck));
        return this;
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

    public NettyServerBuilder httpHandler(HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
        return this;
    }

    HttpHandler httpHandler() {
        return this.httpHandler;
    }

    public NettyServerBuilder setHttpConnector(ServerConnector connector) {
        this.httpConnector = Optional.of(connector);
        return this;
    }

    public NettyServerBuilder setHttpsConnector(ServerConnector connector) {
        this.httpsConnector = Optional.of(connector);
        return this;
    }

    Optional<ServerConnector> httpConnector() {
        return httpConnector;
    }

    Optional<ServerConnector> httpsConnector() {
        return httpsConnector;
    }

    public NettyServerBuilder doOnStartUp(Runnable... startupActions) {
        this.startupActions.addAll(asList(startupActions));
        return this;
    }

    Iterable<Runnable> startupActions() {
        return startupActions;
    }

    public HttpServer build() {
        checkArgument(httpConnector.isPresent() || httpsConnector.isPresent(), "Must configure at least one connector");

        return new NettyServer(this);
    }
}
