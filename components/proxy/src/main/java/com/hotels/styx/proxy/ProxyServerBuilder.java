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
import com.hotels.styx.ServerExecutor;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.netty.NettyServerBuilder;
import org.slf4j.Logger;

import static com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A builder for a ProxyServer.
 */
public final class ProxyServerBuilder {
    private static final Logger LOG = getLogger(ProxyServerBuilder.class);

    private final ResponseInfoFormat responseInfoFormat;
    private final CharSequence styxInfoHeaderName;
    private final ProxyServerConfig serverConfig;
    private final String unwiseCharacters;
    private final Boolean requestTracking;
    private final MetricRegistry metricRegistry;
    private final HttpErrorStatusListener errorListener;

    private HttpHandler handler;
    private ConnectorConfig connectorConfig;
    private ServerExecutor bossExecutor;
    private ServerExecutor workerExecutor;

    public ProxyServerBuilder(Environment environment) {
        this.serverConfig = environment.configuration().proxyServerConfig();
        this.errorListener = environment.errorListener();
        this.metricRegistry = environment.metricRegistry();
        this.requestTracking = environment.configuration().get("requestTracking", Boolean.class).orElse(false);
        this.unwiseCharacters = environment.styxConfig().get(ENCODE_UNWISECHARS).orElse("");
        this.responseInfoFormat = new ResponseInfoFormat(environment);
        this.styxInfoHeaderName = environment.styxConfig().styxHeaderConfig().styxInfoHeaderName();
    }

    public HttpServer build() {
        LOG.info("connectors={} name={}", this.serverConfig.connectors(), "Proxy");

        return NettyServerBuilder.newBuilder()
                .setMetricsRegistry(this.metricRegistry)
                .bossExecutor(bossExecutor)
                .workerExecutor(workerExecutor)
                .setProtocolConnector(
                        new ProxyConnectorFactory(
                                this.serverConfig,
                                this.metricRegistry,
                                this.errorListener,
                                this.unwiseCharacters,
                                this::addInfoHeader,
                                this.requestTracking)
                                .create(connectorConfig))
                .handler(handler)
                .build();
    }

    private LiveHttpResponse.Transformer addInfoHeader(LiveHttpResponse.Transformer responseBuilder, LiveHttpRequest request) {
        return responseBuilder.header(styxInfoHeaderName, responseInfoFormat.format(request));
    }

    public ProxyServerBuilder handler(HttpHandler handler) {
        this.handler = handler;
        return this;
    }

    public ProxyServerBuilder connectorConfig(ConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
        return this;
    }

    public ProxyServerBuilder bossExecutor(ServerExecutor executor) {
        this.bossExecutor = executor;
        return this;
    }

    public ProxyServerBuilder workerExecutor(ServerExecutor executor) {
        this.workerExecutor = executor;
        return this;
    }
}
