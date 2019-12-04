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
package com.hotels.styx.startup;

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.proxy.ProxyServerBuilder;
import com.hotels.styx.server.ConnectorConfig;
import com.hotels.styx.server.HttpServer;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Used to set-up the proxy server for Styx.
 */
public final class ProxyServerSetUp {
    private static final Logger LOG = getLogger(ProxyServerSetUp.class);

    private final HttpHandler styxDataPlane;

    public ProxyServerSetUp(HttpHandler styxDataPlane) {
        this.styxDataPlane = requireNonNull(styxDataPlane);
    }

    public HttpServer createProxyServer(StyxServerComponents config, ConnectorConfig connectorConfig) {
        HttpServer proxyServer = new ProxyServerBuilder(config.environment())
                .handler(styxDataPlane)
                .connectorConfig(connectorConfig)
                .build();

        return proxyServer;
    }
}
