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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;

import static com.hotels.styx.client.HttpRequestOperationFactory.Builder.httpRequestOperationFactoryBuilder;
import static com.hotels.styx.api.extension.service.ConnectionPoolSettings.defaultConnectionPoolSettings;

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

    public static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        new NettyConnectionFactory.Builder()
                                .build()),
                metricRegistry);
    }

    public static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry, int responseTimeoutMillis) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        new NettyConnectionFactory.Builder()
                                .httpRequestOperationFactory(httpRequestOperationFactoryBuilder().responseTimeoutMillis(responseTimeoutMillis).build())
                                .build()),
                metricRegistry);
    }

    private static ConnectionPool poolForOrigin(Origin origin, MetricRegistry metricRegistry, NettyConnectionFactory connectionFactory) {
        return new StatsReportingConnectionPool(
                new SimpleConnectionPool(
                        origin,
                        defaultConnectionPoolSettings(),
                        connectionFactory),
                metricRegistry
        );
    }

    public static ConnectionPool.Factory simplePoolFactory(MetricRegistry metricRegistry) {
        return origin -> poolForOrigin(origin, metricRegistry);
    }

    public static ConnectionPool.Factory simplePoolFactory(BackendService backendService, MetricRegistry metricRegistry) {
        NettyConnectionFactory connectionFactory = new NettyConnectionFactory.Builder()
                .httpRequestOperationFactory(
                        httpRequestOperationFactoryBuilder()
                                .responseTimeoutMillis(backendService.responseTimeoutMillis())
                                .build())
                .build();

        return origin -> poolForOrigin(origin, metricRegistry, connectionFactory);
    }
}
