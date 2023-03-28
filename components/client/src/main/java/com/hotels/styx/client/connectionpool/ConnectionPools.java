/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import com.hotels.styx.metrics.CentralisedMetrics;

import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;
import static com.hotels.styx.client.HttpConfig.newHttpConfigBuilder;
import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;

/**
 * Helper routines for building connection pools with default settings.
 */
public final class ConnectionPools {
    private ConnectionPools() {
    }

    public static ConnectionPool poolForOrigin(Origin origin) {
        return new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), new NettyConnectionFactory.Builder().build());
    }

    public static ConnectionPool.Factory simplePoolFactory() {
        return ConnectionPools::poolForOrigin;
    }

    public static ConnectionPool poolForOrigin(Origin origin, CentralisedMetrics metrics) {
        NettyConnectionFactory factory = new NettyConnectionFactory.Builder().build();

        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(origin, defaultConnectionPoolSettings(), factory), metrics);
    }

    private static ConnectionPool poolForOrigin(ConnectionPoolSettings connectionPoolSettings,
                                                Origin origin,
                                                CentralisedMetrics metrics,
                                                NettyConnectionFactory connectionFactory) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(origin, connectionPoolSettings, connectionFactory), metrics);
    }

    public static ConnectionPool.Factory simplePoolFactory(CentralisedMetrics metrics) {
        return origin -> poolForOrigin(origin, metrics);
    }

    public static ConnectionPool.Factory simplePoolFactory(BackendService backendService, CentralisedMetrics metrics) {
        NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                .httpConfig(newHttpConfigBuilder().setMaxHeadersSize(backendService.maxHeaderSize()).build())
                .httpRequestOperationFactory(
                        httpRequestOperationFactoryBuilder()
                                .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                .metrics(metrics)
                                .build())
                .build();

        return origin -> poolForOrigin(backendService.connectionPoolConfig(), origin, metrics, connectionFactory);
    }
}
