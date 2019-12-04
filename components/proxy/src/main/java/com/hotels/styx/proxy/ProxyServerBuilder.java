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
package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.NettyServerConfig;
import com.hotels.styx.server.netty.eventloop.PlatformAwareServerEventLoopFactory;
import org.slf4j.Logger;

import static com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS;
import static com.hotels.styx.server.netty.eventloop.ServerEventLoopFactories.memoize;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A builder for a ProxyServer.
 */
public final class ProxyServerBuilder {
    private static final Logger LOG = getLogger(ProxyServerBuilder.class);

    private final Environment environment;
    private final ResponseInfoFormat responseInfoFormat;
    private final CharSequence styxInfoHeaderName;

    private HttpHandler handler;
    private ConnectorConfig connectorConfig;

    public ProxyServerBuilder(Environment environment) {
        this.environment = requireNonNull(environment);
        this.responseInfoFormat = new ResponseInfoFormat(environment);
        this.styxInfoHeaderName = environment.styxConfig().styxHeaderConfig().styxInfoHeaderName();
    }

    private ServerEventLoopFactory serverEventLoopFactory(String name, NettyServerConfig serverConfig) {
        return memoize(new PlatformAwareServerEventLoopFactory(name, serverConfig.bossThreadsCount(), serverConfig.workerThreadsCount()));
    }

    public HttpServer build() {
        ProxyServerConfig proxyConfig = environment.styxConfig().proxyServerConfig();
        String unwiseCharacters = environment.styxConfig().get(ENCODE_UNWISECHARS).orElse("");
        boolean requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);

        ProxyServerConfig serverConfig = proxyConfig;

        LOG.info("connectors={} name={}", serverConfig.connectors(), "Proxy");

        HttpServer builder = NettyServerBuilder.newBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .setServerEventLoopFactory(serverEventLoopFactory("Proxy", serverConfig))
                .setProtocolConnector(
                        new ProxyConnectorFactory(
                                proxyConfig,
                                environment.metricRegistry(),
                                environment.errorListener(),
                                unwiseCharacters,
                                this::addInfoHeader,
                                requestTracking)
                                .create(connectorConfig))
                .handler(handler)
                .build();

        return builder;
    }

    private LiveHttpResponse.Transformer addInfoHeader(LiveHttpResponse.Transformer responseBuilder, LiveHttpRequest request) {
        return responseBuilder.header(styxInfoHeaderName, responseInfoFormat.format(request));
    }

    public ProxyServerBuilder handler(HttpHandler handlerFactory) {
        this.handler = handlerFactory;
        return this;
    }

    public ProxyServerBuilder connectorConfig(ConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
        return this;
    }
}
