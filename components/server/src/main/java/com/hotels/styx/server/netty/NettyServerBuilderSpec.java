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

import com.hotels.styx.server.ServerEnvironment;
import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.netty.eventloop.PlatformAwareServerEventLoopFactory;
import org.slf4j.Logger;

import static com.hotels.styx.server.netty.eventloop.ServerEventLoopFactories.memoize;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A specification of a {@link NettyServerBuilder} configuration.
 * <p/>
 * <p>{@code NettyServerBuilderSpec} supports parsing configuration off a yaml string.
 */
public class NettyServerBuilderSpec {
    private static final Logger LOG = getLogger(NettyServerBuilderSpec.class);
    private final String name;
    private final ServerEnvironment environment;
    private final ServerConnectorFactory connectorFactory;

    public NettyServerBuilderSpec(String name, ServerEnvironment environment, ServerConnectorFactory connectorFactory) {
        this.name = requireNonNull(name);
        this.environment = requireNonNull(environment);
        this.connectorFactory = requireNonNull(connectorFactory);
    }

    public NettyServerBuilderSpec() {
        this("Styx", new ServerEnvironment(), new WebServerConnectorFactory());
    }

    public NettyServerBuilder toNettyServerBuilder(NettyServerConfig serverConfig) {
        LOG.info("connectors={} name={}", serverConfig.connectors(), name);

        NettyServerBuilder builder = NettyServerBuilder.newBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .setHealthCheckRegistry(environment.healthCheckRegistry())
                .setServerEventLoopFactory(serverEventLoopFactory(serverConfig));

        serverConfig.httpConnectorConfig().ifPresent(httpConnector ->
                builder.setHttpConnector(connectorFactory.create(httpConnector)));

        serverConfig.httpsConnectorConfig().ifPresent(httpsConnector ->
                builder.setHttpsConnector(connectorFactory.create(httpsConnector)));

        return builder;
    }

    private ServerEventLoopFactory serverEventLoopFactory(NettyServerConfig serverConfig) {
        return memoize(new PlatformAwareServerEventLoopFactory(name, serverConfig.bossThreadsCount(), serverConfig.workerThreadsCount()));
    }
}
